package com.sakura.creator.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Public chat input deliberately excludes internal generation and billing fields. */
public record CreatorChatRequest(
        @NotEmpty @Size(max = 40) List<@NotNull @Valid Message> messages) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported chat field");
    }

    @AssertTrue(message = "Conversation exceeds 32000 characters")
    @JsonIgnore
    public boolean isWithinCharacterLimit() {
        return messages == null || messages.stream()
                .filter(java.util.Objects::nonNull)
                .mapToLong(message -> message.content() == null ? 0 : message.content().length())
                .sum() <= 32000;
    }

    public record Message(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 32000) String content) {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unsupported message field");
        }
    }
}
