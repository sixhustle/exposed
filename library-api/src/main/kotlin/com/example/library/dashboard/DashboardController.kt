package com.example.library.dashboard

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(
	private val dashboardService: DashboardService,
) {

	// suspend 컨트롤러: Spring MVC가 코루틴으로 실행하고, 끝나면 응답을 보낸다 (kotlinx-coroutines-reactor 필요)
	@GetMapping("/api/dashboard")
	suspend fun dashboard(): DashboardResponse = dashboardService.getDashboard()
}
