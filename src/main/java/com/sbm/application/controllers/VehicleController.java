package com.sbm.application.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sbm.application.business.abstracts.CarService;
import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.business.abstracts.VehicleService;
import com.sbm.application.entities.concretes.Vehicle;


@Controller
@RequestMapping("/vehicles")
public class VehicleController {
	private final String controllerName = "vehicles";
	@Autowired
	private VehicleService vehicleService;
	@Autowired
	private CarService carService;
	@Autowired
	private CustomerService customerService;
	@GetMapping("/add")
	public String add(Model model) {
		model.addAttribute("vehicle", new Vehicle());
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "save");
		model.addAttribute("carDetails", carService.getCarDetails().getData());
		model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
		return "app";
	}

	@GetMapping("/edit/{id}")
	public String edit(@PathVariable int id, Model model) {
		var result = vehicleService.getById(id);
		if (result.isSuccess()) {
			model.addAttribute("vehicle", result.getData());
			model.addAttribute("carDetails", carService.getCarDetails().getData());
			model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
			model.addAttribute("controller", controllerName);
			model.addAttribute("page", "save");
			return "app";
		}
		model.addAttribute("toastError", true);
		model.addAttribute("toastMessage",result.getMessage());
		return list(model);

	}

	@PostMapping("/save/{id}")
	public String saveForm(@ModelAttribute("vehicle") Vehicle vehicle, Model model, @PathVariable int id) {
		vehicle.setId(id);
		model.addAttribute("toastMessage", vehicleService.save(vehicle).getMessage());
		model.addAttribute("toastSuccess",true);
		return list(model);
	}

	@GetMapping("/list")
	public String list(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var result = vehicleService.getAll();
		model.addAttribute("vehicles", result.getData());
		model.addAttribute("carDetails", carService.getCarDetails().getData());
		model.addAttribute("customerDetails", customerService.getCustomerDetails().getData());
		return "app";
	}


	@GetMapping("/delete/{id}")
	public String delete(@PathVariable int id, Model model) {
		model.addAttribute("toastMessage", vehicleService.deleteById(id).getMessage());
		model.addAttribute("toastWarning", true);
		return list(model);
	}
}
