package com.sbm.application.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sbm.application.business.abstracts.InsuranceTypeService;

@Controller
@RequestMapping("/insuranceTypes")
public class InsuranceTypeController {

	private final String controllerName = "insuranceTypes";
	@Autowired
	private InsuranceTypeService insuranceTypeService;
	@GetMapping("/select")
	public String insuranceSelector(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "select");
		var result = insuranceTypeService.getAll();
		if(!result.isSuccess()) {
			model.addAttribute("toastError", true);
			model.addAttribute("toastMessage", result.getMessage());
			model.addAttribute("page", "list");
			return "app";
		}
		model.addAttribute("insuranceTypes", result.getData());
		return "app";
	}
}
