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
import com.sbm.application.entities.concretes.Car;


@Controller
@RequestMapping("/cars")
public class CarController {
	private final String controllerName = "cars";
	@Autowired
	private CarService carService;
	@GetMapping("/add")
	public String add(Model model) {
		model.addAttribute("car", new Car());
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "save");
		return "app";
	}

	@GetMapping("/edit/{id}")
	public String edit(@PathVariable int id, Model model) {
		var result = carService.getById(id);
		if (result.isSuccess()) {
			model.addAttribute("car", result.getData());
			model.addAttribute("controller", controllerName);
			model.addAttribute("page", "save");
			return "app";
		}
		model.addAttribute("carNotFound", true);
		return list(model);

	}

	@PostMapping("/save/{id}")
	public String saveForm(@ModelAttribute("customer") Car car, Model model, @PathVariable int id) {
		car.setId(id);
		carService.save(car);
		model.addAttribute("carSaved",true);
		return list(model);
	}

	@GetMapping("/list")
	public String list(Model model) {
		model.addAttribute("controller", controllerName);
		model.addAttribute("page", "list");
		var result = carService.getAll();
		model.addAttribute("cars", result.getData());
		return "app";
	}


	@GetMapping("/delete/{id}")
	public String delete(@PathVariable int id, Model model) {
		carService.deleteById(id);
		model.addAttribute("carDeleted", true);
		return list(model);
	}
}
