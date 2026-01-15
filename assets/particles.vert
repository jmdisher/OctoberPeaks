#version 100

uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform float uAnimationFraction;

attribute float aAnimationOffset;
attribute vec3 aStartPosition;
attribute vec3 aEndPosition;
attribute vec3 aColour;

varying float vAnimation;
varying vec3 vColour;


void main()
{
	vec3 delta = aEndPosition - aStartPosition;
	float animationFraction = mod(uAnimationFraction + 1.0 - aAnimationOffset, 1.0);
	vec3 position = aStartPosition + animationFraction * delta;
	vec4 viewPosition = uViewMatrix * vec4(position, 1.0);
	
	vAnimation = animationFraction;
	vColour = aColour;
	gl_PointSize = 20.0 / length(viewPosition.xyz);
	gl_Position = uProjectionMatrix * uViewMatrix * vec4(position, 1.0);
}
