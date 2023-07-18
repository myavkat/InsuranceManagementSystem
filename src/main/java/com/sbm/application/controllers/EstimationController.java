package com.sbm.application.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.business.abstracts.EstimationService;
import com.sbm.application.business.abstracts.VehicleService;

@Controller
@RequestMapping("/estimations")
public class EstimationController {

	private final String controllerName = "estimations";
	@Autowired
	private EstimationService estimationService;
	@Autowired
	private CustomerService customerService;
	@Autowired
	private VehicleService vehicleService;

	@GetMapping("/kasko")
	public String estimateKasko(Model model, @RequestParam(required=false) Integer customerId, @RequestParam(required=false) Integer vehicleId) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "kasko");
		if(customerId == null && vehicleId == null) {
			model.addAttribute("customers",customerService.getCustomerDetails().getData());
			return "app";
		}
		else if(vehicleId == null) {
			model.addAttribute("vehicles",vehicleService.getVehiclesByCustomerId(customerId).getData());
			return "app";
		}
		var result = estimationService.estimateKaskoAllCompanies(vehicleId);
		if (!result.isSuccess()) {
			model.addAttribute("toastError", true);
			model.addAttribute("toastMessage", result.getMessage());
			return "app";
		}
		model.addAttribute("estimations", result.getData());
		return "app";
	}
	
	@GetMapping("/list")
	public String list(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var estimationResult = estimationService.getDetails();
		if(!estimationResult.isSuccess()) {
			return "redirect:/";
		}
		model.addAttribute("estimationDetails",estimationResult.getData());
		return "app";
	}
	
	@GetMapping("/confirm/{id}")
	public String confirm(Model model, @PathVariable int id) {
		var confirmResult = estimationService.confirmById(id);
		if(confirmResult.isSuccess()){
			model.addAttribute("toastSuccess", true);
			model.addAttribute("toastMessage", "Teklif kabul edildi");
			return list(model);
		}
		model.addAttribute("toastError", true);
		model.addAttribute("toastMessage", confirmResult.getMessage());
		return list(model);
	}
	

}
