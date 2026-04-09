package net.tecgurus.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "counters")
public class DbSequence {
    @Id
    private String id;   // e.g. "quote"
    private long seq;
}
