package com.sbm.application.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sbm.application.business.abstracts.CityService;
import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.business.abstracts.ProfessionService;
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
	public String saveForm(@ModelAttribute("customer") Customer customer, Model model, @PathVariable int id) {
		customer.setId(id);
		customerService.save(customer);
		model.addAttribute("customerSaved",true);
		return list(model);
	}

	@GetMapping("/list")
	public String list(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var result = customerService.getAll();
		model.addAttribute("customers", result.getData());
		return "app";
	}


	@GetMapping("/delete/{id}")
	public String delete(@PathVariable int id, Model model) {
		customerService.deleteById(id);
		model.addAttribute("customerDeleted", true);
		return list(model);
	}
}
