package com.sbm.application.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.entities.concretes.Customer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/customers")
public class CustomerController {
	private final String controllerName = "customers";
	@Autowired
	private CustomerService customerService;

	@GetMapping("/add")
	public String addCustomer(Model model) {
		model.addAttribute("customer", new Customer());
		return "app";
	}

	@GetMapping("/edit/{id}")
	public String editCustomer(@PathVariable int id, Model model) {
		var result = customerService.getById(id);
		model.addAttribute("controller", controllerName);
		if (result.isSuccess()) {
			model.addAttribute("customer", result.getData());
			model.addAttribute("page", "edit");
		}
		model.addAttribute("page", "list");
		model.addAttribute("toast", "customerNotFound");
		return "app";

	}

	@PostMapping("/save")
	public String saveCustomerForm(@ModelAttribute("customer") Customer customer, Model model) {
		customerService.save(customer);
		return "app";
	}

	@GetMapping("/list")
	public String getCustomers(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var result = customerService.getAll();
		model.addAttribute("customers", result.getData());
		return "app";
	}
}
