package com.example.eventmanagement.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Attendee review, stored in `event_reviews`. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Review {
    private String authorName;
    private Integer rating;
    private String comment;
}
