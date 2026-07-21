package com.itcabs.dispatch

import com.itcabs.shared.ApiException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * The M1 invariant as an automated test: N verified drivers race for ONE leg;
 * exactly one wins, the rest get 409. Backed by a real Postgres (Testcontainers),
 * exercising the same atomic UPDATE proven manually against local Postgres.
 */
@SpringBootTest
@Testcontainers
class ClaimConcurrencyTest {

    @Autowired lateinit var dispatch: DispatchService
    @Autowired lateinit var db: NamedParameterJdbcTemplate

    @Test
    fun `exactly one driver wins a contested leg`() {
        val coordinatorId = insertUser("+910000000001", "COORDINATOR")
        val jobId = db.queryForObject(
            "INSERT INTO jobs(coordinator_id, office, shift) VALUES (:c,'Office','Shift') RETURNING id",
            MapSqlParameterSource("c", coordinatorId), Long::class.java,
        )!!
        val legId = db.queryForObject(
            """INSERT INTO legs(job_id, coordinator_id, pickup, drop_point, fare_paise)
               VALUES (:j,:c,'A','B',42000) RETURNING id""",
            MapSqlParameterSource().addValue("j", jobId).addValue("c", coordinatorId), Long::class.java,
        )!!

        val n = 50
        val drivers = (1..n).map { insertVerifiedDriver("+9199999${"%05d".format(it)}") }

        val wins = AtomicInteger(0)
        val conflicts = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(n)
        drivers.forEach { d ->
            pool.submit {
                try { dispatch.claim(d, legId); wins.incrementAndGet() }
                catch (e: ApiException) { conflicts.incrementAndGet() }
            }
        }
        pool.shutdown()
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(1, wins.get(), "exactly one winner")
        assertEquals(n - 1, conflicts.get(), "everyone else loses")
        assertEquals("CLAIMED", db.queryForObject(
            "SELECT status FROM legs WHERE id=:id", MapSqlParameterSource("id", legId), String::class.java))
    }

    private fun insertUser(phone: String, role: String): Long = db.queryForObject(
        "INSERT INTO users(phone, role, name) VALUES (:p,:r,'x') RETURNING id",
        MapSqlParameterSource().addValue("p", phone).addValue("r", role), Long::class.java,
    )!!

    private fun insertVerifiedDriver(phone: String): Long {
        val id = insertUser(phone, "DRIVER")
        db.update(
            """INSERT INTO driver_profiles(user_id, vehicle_type, vehicle_reg, kyc_status)
               VALUES (:u,'Sedan','REG','VERIFIED')""",
            MapSqlParameterSource("u", id),
        )
        return id
    }

    companion object {
        @Container @JvmStatic
        val pg: PostgreSQLContainer<*> = PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName.parse("postgres:16"),
        ).withDatabaseName("itcabs")

        @DynamicPropertySource @JvmStatic
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url", pg::getJdbcUrl)
            r.add("spring.datasource.username", pg::getUsername)
            r.add("spring.datasource.password", pg::getPassword)
        }
    }
}
