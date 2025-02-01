#version 100
precision mediump float;

uniform samplerCube uTexture0;
uniform samplerCube uTexture1;
uniform float uSkyFraction;

varying vec3 vTexturePosition;

void main()
{
	vec4 nightTexel = textureCube(uTexture0, vTexturePosition);
	vec4 dayTexel = textureCube(uTexture1, vTexturePosition);
	gl_FragColor = mix(nightTexel, dayTexel, uSkyFraction);
}
