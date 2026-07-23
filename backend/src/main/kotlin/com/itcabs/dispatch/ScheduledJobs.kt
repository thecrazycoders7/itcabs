package com.itcabs.dispatch

import com.itcabs.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Background sweeps (M-scheduling):
 *  - recurring templates auto-post daily,
 *  - OPEN legs left unclaimed too long escalate to the coordinator (once).
 * ponytail: single-instance cron on the pilot's one Render dyno. Move to a locked/queued scheduler
 * if we ever run more than one backend instance, so a sweep doesn't double-fire.
 */
@Component
class ScheduledJobs(
    private val templates: TemplateService,
    private val push: PushService,
    private val db: NamedParameterJdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Post recurring templates for the day. Runs at 04:30 IST-ish (server TZ) and hourly as a catch-up. */
    @Scheduled(cron = "0 30 4 * * *")
    @Scheduled(fixedRate = 3_600_000L)
    fun postRecurring() {
        runCatching { templates.postDueRecurring() }
            .onSuccess { if (it > 0) log.info("auto-posted {} recurring template(s)", it) }
            .onFailure { log.warn("recurring auto-post failed: {}", it.message) }
    }

    /** Every 5 min: warn coordinators about trips still OPEN >20 min after posting (once per leg). */
    @Scheduled(fixedRate = 300_000L)
    fun escalateStaleOpenLegs() {
        val stale = db.queryForList(
            """SELECT coordinator_id, count(*) AS n
                 FROM legs
                WHERE status='OPEN' AND escalated_at IS NULL AND created_at < now() - interval '20 minutes'
                GROUP BY coordinator_id""",
            MapSqlParameterSource(),
        )
        stale.forEach { row ->
            val coordId = (row["coordinator_id"] as Number).toLong()
            val n = (row["n"] as Number).toInt()
            push.notifyUser(coordId, "Trips still unclaimed", "$n trip${if (n > 1) "s" else ""} you posted are still open — no driver yet.")
        }
        if (stale.isNotEmpty()) {
            db.update(
                "UPDATE legs SET escalated_at=now() WHERE status='OPEN' AND escalated_at IS NULL AND created_at < now() - interval '20 minutes'",
                MapSqlParameterSource(),
            )
        }
    }
}
