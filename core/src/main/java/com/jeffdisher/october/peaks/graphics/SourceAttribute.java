package com.jeffdisher.october.peaks.graphics;


/**
 * Describes a vertex attribute as it will be used in the source.  This may not actually end up existing in the program
 * so writes to this might be ignored.
 */
public record SourceAttribute(String name, int floats)
{}
