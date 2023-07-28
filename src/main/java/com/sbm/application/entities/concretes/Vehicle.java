package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.entities.abstracts.Entity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("Vehicles")
public class Vehicle extends Entity {

	@NotNull(message = "Boş geçilemez!")
	@Column("CustomerId")
	private int customerId;
	@NotNull(message = "Boş geçilemez!")
	@Column("CarId")
	private int carId;
	@NotNull(message = "Boş geçilemez!")
	@Column("Damaged")
	private boolean damaged;
	@NotNull(message = "Boş geçilemez!")
	@Pattern(regexp = "^(0[1-9]|[1-7][0-9]|8[0-1])(([A-PR-VYZ]{1})(?!0{4,5}$)\\d{4,5}|([A-PR-VYZ]{2})(?!0{3,4}$)\\d{3,4}|([A-PR-VYZ]{3})(?!0{2,3}$)\\d{2,3})$"
	, message = "Plakayı arasında boşluk olmadan büyük harflerle giriniz!")
	@Column("LicensePlate")
	private String licensePlate;
	@NotNull(message = "Boş geçilemez!")
	@Pattern(regexp = "^[(A-H|J-N|P|R-Z|0-9)]{17}$", message = "Şasi Numarası Geçersiz!")
	@Column("VIN")
	private String vin;

	public String extractCityCode() {
		return licensePlate.substring(0, 2);
	}

}
