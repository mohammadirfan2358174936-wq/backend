package com.example.eventmanagement.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embedded value objects stored in `event_speakers`. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Speaker {
    private String name;
    private String title;
    private String company;
    private String photoUrl;
}
