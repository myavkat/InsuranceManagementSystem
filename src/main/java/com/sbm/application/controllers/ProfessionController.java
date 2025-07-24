package com.sbm.application.controllers;

import java.util.ArrayList;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sbm.application.entities.concretes.Profession;
import com.sbm.application.repositories.concretes.ProfessionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/professions")
public class ProfessionController {
	private final ProfessionRepository professionRepository;

	public ProfessionController(ProfessionRepository professionRepository) {
		this.professionRepository = professionRepository;
	}
	@GetMapping("/list")
	public String GetAll(Model model) {
		ArrayList<Profession> professions = new ArrayList<Profession>();
		professionRepository.findAll().doOnNext(professions::add).blockLast();
		model.addAttribute("professions", professions);
		model.addAttribute("title", "Meslek Listesi");
		model.addAttribute("controller", "professions");
		model.addAttribute("page", "list");
		return "app";
	}
	
	@PostMapping("/create")
	@ResponseBody
	public Mono<Profession> Create(@RequestBody Profession profession) {
		return professionRepository.save(profession);
	}
	
	@GetMapping("/api/getall")
	@ResponseBody
	public Flux<Profession> GetAll() {
		return professionRepository.findAll();
	}

	
	@GetMapping("/get")
	@ResponseBody
	public Mono<Profession> Get(@RequestParam(required=true)int id) {
		return professionRepository.findById(id);
	}
	
}
