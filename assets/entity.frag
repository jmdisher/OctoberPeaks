#version 100
precision mediump float;

uniform sampler2D uTexture0;
uniform float uDamage;
uniform float uBrightness;
uniform float uOpacity;

varying vec2 vTexture0;


void main()
{
	vec4 texture = texture2D(uTexture0, vTexture0);
	gl_FragColor = vec4(uBrightness * texture.rgb, texture.a);
	
	// We just add the base colour in since it will clamp on overflow.
	gl_FragColor.r += uDamage;
	
	// We will modify the opacity for the cases of a dying entity.
	gl_FragColor.a *= uOpacity;
}
