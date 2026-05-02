package com.sbm.application.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sbm.application.business.abstracts.CityService;
import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.business.abstracts.RealEstateService;
import com.sbm.application.business.abstracts.RealEstateUnitConstructionCostService;
import com.sbm.application.entities.concretes.RealEstate;

@Controller
@RequestMapping("/realEstates")
public class RealEstateController {
	private final String controllerName = "realEstates";
	@Autowired
	private RealEstateService realEstateService;
	@Autowired
	private CityService cityService;
	@Autowired
	private CustomerService customerService;
	@Autowired
	private RealEstateUnitConstructionCostService unitConstructionCostService;

	@GetMapping("/add")
	public String add(Model model) {
		model.addAttribute("realEstate", new RealEstate());
		model.addAttribute("controller", controllerName);
		model.addAttribute("cityDetails", cityService.getAll().getData());
		model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
		model.addAttribute("unitConstructionCosts", unitConstructionCostService.getAllDetails().getData());
		model.addAttribute("page", "save");
		return "app";
	}

	@GetMapping("/edit/{id}")
	public String edit(@PathVariable int id, Model model) {
		var result = realEstateService.getById(id);
		if (result.isSuccess()) {
			model.addAttribute("realEstate", result.getData());
			model.addAttribute("controller", controllerName);
			model.addAttribute("cityDetails", cityService.getAll().getData());
			model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
			model.addAttribute("unitConstructionCosts", unitConstructionCostService.getAllDetails().getData());
			model.addAttribute("page", "save");
			return "app";
		}
		model.addAttribute("toastError", true);
		model.addAttribute("toastMessage", result.getMessage());
		return list(model);

	}

	@PostMapping("/save/{id}")
	public String saveForm(@Validated @ModelAttribute RealEstate realEstate, BindingResult bindingResult,
			Model model, @PathVariable int id) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("toastWarning", true);
			model.addAttribute("toastMessage", "Hatalı alanlar var");
			model.addAttribute("controller", controllerName);
			model.addAttribute("cityDetails", cityService.getAll().getData());
			model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
			model.addAttribute("unitConstructionCosts", unitConstructionCostService.getAllDetails().getData());
			model.addAttribute("page", "save");
			return "app";
		}
		realEstate.setId(id);
		var saveResult = realEstateService.save(realEstate);
		model.addAttribute("toastMessage", saveResult.getMessage());
		if (!saveResult.isSuccess()) {
			model.addAttribute("toastError", true);
			model.addAttribute("controller", controllerName);
			model.addAttribute("cityDetails", cityService.getAll().getData());
			model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
			model.addAttribute("unitConstructionCosts", unitConstructionCostService.getAllDetails().getData());
			model.addAttribute("page", "save");
			return "app";
		}
		model.addAttribute("toastSuccess", true);
		return list(model);
	}

	@GetMapping("/list")
	public String list(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var result = realEstateService.getAllDetails();
		model.addAttribute("realEstateDetails", result.getData());
		return "app";
	}

	@GetMapping("/delete/{id}")
	public String delete(@PathVariable int id, Model model) {
		model.addAttribute("toastMessage", realEstateService.deleteById(id).getMessage());
		model.addAttribute("toastWarning", true);
		return list(model);
	}
}
