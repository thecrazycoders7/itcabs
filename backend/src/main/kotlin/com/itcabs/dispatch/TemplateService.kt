package com.itcabs.dispatch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.itcabs.shared.badRequest
import com.itcabs.shared.forbidden
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Saved routes (M-scheduling): a coordinator stores a route's shape once and re-posts it with one
 * tap, or marks it recurring so a daily sweep auto-posts it — killing the every-morning re-entry.
 */
@Service
class TemplateService(
    private val db: NamedParameterJdbcTemplate,
    private val dispatch: DispatchService,
    private val mapper: ObjectMapper,
) {
    fun list(coordinatorId: Long): List<TemplateDto> = db.query(
        "SELECT id, name, office, shift, vehicle_type, legs_json, recurring FROM job_templates WHERE coordinator_id=:c ORDER BY name",
        MapSqlParameterSource("c", coordinatorId), rowMapper,
    )

    fun save(coordinatorId: Long, input: TemplateInput): TemplateDto {
        if (input.legs.isEmpty()) throw badRequest("a template needs at least one leg")
        val id = db.queryForObject(
            """INSERT INTO job_templates(coordinator_id, name, office, shift, vehicle_type, legs_json, recurring)
               VALUES (:c,:n,:o,:s,:vt, CAST(:legs AS jsonb), :r) RETURNING id""",
            MapSqlParameterSource().addValue("c", coordinatorId).addValue("n", input.name)
                .addValue("o", input.office).addValue("s", input.shift).addValue("vt", input.vehicleType)
                .addValue("legs", mapper.writeValueAsString(input.legs)).addValue("r", input.recurring),
            Long::class.java,
        )!!
        return list(coordinatorId).first { it.id == id }
    }

    fun delete(coordinatorId: Long, id: Long) {
        val n = db.update(
            "DELETE FROM job_templates WHERE id=:id AND coordinator_id=:c",
            MapSqlParameterSource().addValue("id", id).addValue("c", coordinatorId),
        )
        if (n == 0) throw forbidden("template not found, or not yours")
    }

    /** Post a fresh OPEN job from a saved template. */
    fun postFrom(coordinatorId: Long, id: Long): List<LegDto> {
        val t = list(coordinatorId).firstOrNull { it.id == id } ?: throw badRequest("template not found")
        return dispatch.postJob(coordinatorId, PostJobInput(t.office, t.shift, t.legs))
    }

    /** Auto-post every recurring template that hasn't posted yet today. Idempotent per day. */
    fun postDueRecurring(): Int {
        val due = db.query(
            "SELECT id, coordinator_id, name, office, shift, vehicle_type, legs_json, recurring FROM job_templates WHERE recurring = true AND (last_posted_on IS NULL OR last_posted_on < :today)",
            MapSqlParameterSource("today", LocalDate.now()),
        ) { rs, _ -> rs.getLong("coordinator_id") to rowMapper.mapRow(rs, 0)!! }
        due.forEach { (coordId, t) ->
            dispatch.postJob(coordId, PostJobInput(t.office, t.shift, t.legs))
            db.update(
                "UPDATE job_templates SET last_posted_on = :today WHERE id = :id",
                MapSqlParameterSource().addValue("today", LocalDate.now()).addValue("id", t.id),
            )
        }
        return due.size
    }

    private val rowMapper = RowMapper { rs, _ ->
        TemplateDto(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            office = rs.getString("office"),
            shift = rs.getString("shift"),
            vehicleType = rs.getString("vehicle_type"),
            legs = mapper.readValue(rs.getString("legs_json")),
            recurring = rs.getBoolean("recurring"),
        )
    }
}
