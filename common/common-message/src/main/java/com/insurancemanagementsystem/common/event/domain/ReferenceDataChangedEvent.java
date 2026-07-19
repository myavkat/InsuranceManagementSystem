package com.insurancemanagementsystem.common.event.domain;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceDataChangedEvent extends BaseEvent {

	private String entityType;

	private String changeType;

	@Override
	public String getEventType() {
		return EventConstants.REFERENCE_DATA_CHANGED;
	}

}
