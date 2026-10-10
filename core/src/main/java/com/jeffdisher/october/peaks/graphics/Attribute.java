package com.jeffdisher.october.peaks.graphics;


/**
 * Describes a vertex attribute as it exists in the shader program.  Note that an index of -1 means that this attribute
 * does not exist in the linked program so writes to it will be ignored.
 */
public record Attribute(String name, int location, int floats)
{}
