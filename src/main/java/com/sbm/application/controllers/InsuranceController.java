package com.sbm.application.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sbm.application.business.abstracts.InsuranceService;
@Controller
@RequestMapping("/insurances")
public class InsuranceController {

	private final String controllerName = "insurances";
	@Autowired
	private InsuranceService insuranceService;

	@GetMapping("/calculate/{name}")
	public String calculate(@PathVariable String name, Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", name + "Calculate");
		return "app";
	}

	@PostMapping("/calculate/{name}")
	public String submitCalculate(@PathVariable String name, Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", name + "Success");
		return "app";
	}

	@GetMapping("/details/{name}")
	public String details(@PathVariable String name, Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", name + "Details");
		return "app";
	}

	@GetMapping("/list")
	public String getAll(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		model.addAttribute("insurances", insuranceService.getAll().getData());
		return "app";
	}

}
