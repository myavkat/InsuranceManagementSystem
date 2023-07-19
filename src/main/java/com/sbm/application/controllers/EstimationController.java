package com.sbm.application.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.business.abstracts.EstimationService;
import com.sbm.application.business.abstracts.VehicleService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.dtos.EstimationDetailDTO;

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
	public String estimateKasko(Model model, @RequestParam(required = false) Integer customerId,
			@RequestParam(required = false) Integer vehicleId) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "kasko");
		if (customerId == null && vehicleId == null) {
			model.addAttribute("customers", customerService.getCustomerDetails().getData());
			return "app";
		} else if (vehicleId == null) {
			model.addAttribute("vehicles", vehicleService.getVehiclesByCustomerId(customerId).getData());
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
	public String list(Model model, @RequestParam(required = false) Integer customerId,
			@RequestParam(required = false) Integer vehicleId) {
		DataResult<List<EstimationDetailDTO>> estimationResult;
		if (vehicleId != null) {
			estimationResult = estimationService.getDetailsByVehicleId(vehicleId);
		} else if (customerId != null) {
			estimationResult = estimationService.getDetailsByCustomerId(customerId);
		} else {
			estimationResult = estimationService.getDetails();
		}
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		if (!estimationResult.isSuccess()) {
			return "redirect:/";
		}
		model.addAttribute("estimationDetails", estimationResult.getData());
		return "app";
	}

	@GetMapping("/confirm/{id}")
	public String confirm(Model model, @PathVariable int id) {
		var confirmResult = estimationService.confirmById(id);
		if (confirmResult.isSuccess()) {
			model.addAttribute("toastSuccess", true);
			model.addAttribute("toastMessage", "Teklif kabul edildi");
			return list(model, null, null);
		}
		model.addAttribute("toastError", true);
		model.addAttribute("toastMessage", confirmResult.getMessage());
		return list(model, null, null);
	}

	@GetMapping("/revokeConfirmation/{id}")
	public String revoke(Model model, @PathVariable int id) {
		var confirmResult = estimationService.revokeConfirmationById(id);
		if (confirmResult.isSuccess()) {
			model.addAttribute("toastSuccess", true);
			model.addAttribute("toastMessage", "Teklif onayı geri alındı");
			return list(model, null, null);
		}
		model.addAttribute("toastError", true);
		model.addAttribute("toastMessage", confirmResult.getMessage());
		return list(model, null, null);
	}
	
	@GetMapping("/delete/{id}")
	public String delete(Model model, @PathVariable int id) {
		var deleteResult = estimationService.deleteById(id);
		if(!deleteResult.isSuccess()) {
			model.addAttribute("toastError", true);
			model.addAttribute("toastMessage", deleteResult.getMessage());
			return list(model, null, null);
		}
		model.addAttribute("toastSuccess", true);
		model.addAttribute("toastMessage", deleteResult.getMessage());
		return list(model, null, null);
	}

}
