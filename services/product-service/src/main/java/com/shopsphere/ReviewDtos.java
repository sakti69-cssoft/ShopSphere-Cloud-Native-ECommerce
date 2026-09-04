package com.shopsphere;

import jakarta.validation.constraints.*;

public final class ReviewDtos {
  private ReviewDtos() {}
  public record Request(@Min(1) @Max(5) int rating,@Size(max=120) String title,@NotBlank @Size(max=2000) String text) {}
  public record Stats(double average,long count) {}
}
