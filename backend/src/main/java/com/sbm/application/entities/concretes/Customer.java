package com.sbm.application.entities.concretes;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.sbm.application.core.helpers.DateHelper;
import com.sbm.application.entities.abstracts.Entity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("Customers")
public class Customer extends Entity {

	@NotNull(message = "Boş geçilemez!")
	@Column("professionId")
	private int professionId;
	@NotNull(message = "Boş geçilemez!")
	@Column("cityOfResidenceId")
	private int cityOfResidenceId;
	@NotNull(message = "Boş geçilemez!")
	@Size(min = 3, max=50,message = "3 ile 50 karakter arası olmalıdır!")
	@Column("firstName")
	private String firstName;
	@NotNull(message = "Boş geçilemez!")
	@Size(min = 3, max=50,message = "3 ile 50 karakter arası olmalıdır!")
	@Column("lastName")
	private String lastName;
	@NotNull(message = "Boş geçilemez!")
	@Size(min = 11, max=11,message = "TC 11 haneli olmalıdır!")
	@Column("tc")
	private String tc;
	@NotNull(message = "Boş geçilemez!")
	@Pattern(regexp = "^\\d{3}[-\\s]?\\d{3}[-\\s]?\\d{4}$", message = "Telefon numarasını başında 0 ve boşluk olmadan giriniz!")
	@Column("phoneNumber")
	private String phoneNumber;
	@NotNull(message = "Boş geçilemez!")
	@Pattern(regexp = "^(\\d{4}-\\d{2}-\\d{2})$", message="Geçersiz tarih")
	@Column("birthDate")
	private String birthDate;
	@NotNull(message = "Boş geçilemez!")
	@Pattern(regexp = "^(\\d{4}-\\d{2}-\\d{2})$", message="Geçersiz tarih")
	@Column("licenseObtainedAt")
	private String licenseObtainedAt;

	public int getAge() {
		return DateHelper.getYearDifferenceFromNow(birthDate);
	}
	public int getLicenseAge() {
		return DateHelper.getYearDifferenceFromNow(licenseObtainedAt);
	}

}
