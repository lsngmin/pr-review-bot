package com.pawranoid

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class PawranoidApplication

fun main(args: Array<String>) {
    runApplication<PawranoidApplication>(*args)
}
