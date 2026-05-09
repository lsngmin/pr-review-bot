package com.pbot.bot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class PrReviewBotApplication

fun main(args: Array<String>) {
	runApplication<PrReviewBotApplication>(*args)
}
