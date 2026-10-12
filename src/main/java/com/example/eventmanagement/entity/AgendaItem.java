package com.example.eventmanagement.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row of the event schedule, stored in `event_agenda`. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgendaItem {
    private Instant startsAt;
    private String title;
    private String description;
    private String speaker;
}
