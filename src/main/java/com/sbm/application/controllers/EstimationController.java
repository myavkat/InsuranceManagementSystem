package com.sbm.application.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sbm.application.business.abstracts.EstimationService;

@Controller
@RequestMapping("/estimations")
public class EstimationController {

	private final String controllerName = "estimations";
	@Autowired
	private EstimationService estimationService;
	
	@PostMapping("/kasko")
	public String estimateKasko(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "kasko");
		return "app";
	}
	
	@GetMapping("/kasko")
	public String estimateKaskoForm(Model model, @RequestParam int insuranceId, @RequestParam int vehicleId) {
		var result = estimationService.estimateKasko(insuranceId, vehicleId);
		model.addAttribute("controller", controllerName);
		if(!result.isSuccess()) {
			model.addAttribute("page", "list");
			model.addAttribute("toastError", true);
			model.addAttribute("toastMessage", result.getMessage());
			return "app";
		}
		model.addAttribute("page", "success");
		model.addAttribute("estimation", result.getData());
		return "app";
	}
	
}
