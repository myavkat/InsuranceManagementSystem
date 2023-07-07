package com.sbm.application.controllers;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sbm.application.entities.concretes.*;
import com.sbm.application.repositories.concretes.*;

@Controller
@RequestMapping("/insurances")
public class InsuranceController {

	private final String controllerName = "insurances";
	@Autowired
	private InsuranceRepository insuranceRepository;
	@Autowired
	private CityRepository cityRepository;
	@Autowired
	private ProfessionRepository professionRepository;
	@Autowired
	private CustomerRepository customerRepository;

	@GetMapping("/calculate/{name}")
	public String calculate(@PathVariable String name, Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", name + "Calculate");
		Customer customer = new Customer();
		ArrayList<Profession> professions = new ArrayList<Profession>();
		professionRepository.findAll().doOnNext(i -> professions.add(i)).blockLast();
		ArrayList<City> cities = new ArrayList<City>();
		cityRepository.findAll().doOnNext(i -> cities.add(i)).blockLast();
		model.addAttribute("customer", customer);
		model.addAttribute("professions", professions);
		model.addAttribute("cities", cities);

		return "app";
	}

	@PostMapping("/calculate/{name}")
	public String submitCalculate(@ModelAttribute("customer") Customer customer, @PathVariable String name,
			Model model) {
		customerRepository.save(customer).block();
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
		ArrayList<Insurance> insurances = new ArrayList<Insurance>();
		insuranceRepository.findAll().doOnNext(i -> insurances.add(i)).blockLast();
		model.addAttribute("insurances", insurances);
		return "app";
	}

}
