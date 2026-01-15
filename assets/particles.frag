#version 100
precision mediump float;

uniform sampler2D uTexture0;
uniform float uBrightness;

varying float vAnimation;
varying vec3 vColour;


void main()
{
	vec4 texture = texture2D(uTexture0, gl_PointCoord);
	vec3 brightColour = uBrightness * vColour;
	gl_FragColor = vec4(brightColour.rgb, texture.a * (1.0 - vAnimation));
}
