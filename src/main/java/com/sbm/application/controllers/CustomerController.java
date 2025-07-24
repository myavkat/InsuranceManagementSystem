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
import com.sbm.application.business.abstracts.ProfessionService;
import com.sbm.application.entities.concretes.Customer;

@Controller
@RequestMapping("/customers")
public class CustomerController {
	private final String controllerName = "customers";
	@Autowired
	private CustomerService customerService;
	@Autowired
	private ProfessionService professionService;
	@Autowired
	private CityService cityService;

	@GetMapping("/add")
	public String add(Model model) {
		model.addAttribute("customer", new Customer());
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "save");
		model.addAttribute("professions", professionService.getAll().getData());
		model.addAttribute("cities", cityService.getAll().getData());
		return "app";
	}

	@GetMapping("/edit/{id}")
	public String edit(@PathVariable int id, Model model) {
		var result = customerService.getById(id);
		if (result.isSuccess()) {
			model.addAttribute("customer", result.getData());
			model.addAttribute("controller", controllerName);
			model.addAttribute("page", "save");
			model.addAttribute("professions", professionService.getAll().getData());
			model.addAttribute("cities", cityService.getAll().getData());
			return "app";
		}
		model.addAttribute("customerNotFound", true);
		return list(model);

	}

	@PostMapping("/save/{id}")
	public String saveForm(@Validated @ModelAttribute Customer customer, BindingResult bindingResult,
			Model model, @PathVariable int id) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("toastWarning", true);
			model.addAttribute("toastMessage", "Hatalı alanlar var");
			model.addAttribute("controller", controllerName);
			model.addAttribute("professions", professionService.getAll().getData());
			model.addAttribute("cities", cityService.getAll().getData());
			model.addAttribute("page", "save");
			return "app";
		}
		customer.setId(id);
		var saveResult = customerService.save(customer);
		if (saveResult.isSuccess()) {
			model.addAttribute("toastSuccess", true);
			model.addAttribute("toastMessage", "Müşteri kaydedildi.");
			return list(model);
		}
		else {
			model.addAttribute("toastError", true);
			model.addAttribute("toastMessage", saveResult.getMessage());
			model.addAttribute("controller", controllerName);
			model.addAttribute("professions", professionService.getAll().getData());
			model.addAttribute("cities", cityService.getAll().getData());
			model.addAttribute("page", "save");
			return "app";
		}
	}

	@GetMapping("/list")
	public String list(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var result = customerService.getCustomerDetails();
		model.addAttribute("customerDetails", result.getData());
		return "app";
	}

	@GetMapping("/delete/{id}")
	public String delete(@PathVariable int id, Model model) {
		model.addAttribute("toastMessage", customerService.deleteById(id).getMessage());
		model.addAttribute("toastWarning", true);
		return list(model);
	}
}
