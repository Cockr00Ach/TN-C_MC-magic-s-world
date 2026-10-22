#version 150
uniform sampler2D Scene;
in vec2 texCoord;
out vec4 fragColor;
void main(){vec4 c=texture(Scene,texCoord);float l=dot(c.rgb,vec3(0.2126,0.7152,0.0722));fragColor=vec4(mix(c.rgb,vec3(l*.94+.025),.94),c.a);}
