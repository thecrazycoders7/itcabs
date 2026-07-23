package com.itcabs

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling // recurring-template auto-post + unfilled-trip escalation sweeps
class ItcabsApplication

fun main(args: Array<String>) {
    runApplication<ItcabsApplication>(*args)
}
