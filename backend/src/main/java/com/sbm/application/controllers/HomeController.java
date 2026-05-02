package com.sbm.application.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	private final String controllerName = "home";
	@GetMapping("/")
	public String Index(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "index");
		return "app";
	}
}
