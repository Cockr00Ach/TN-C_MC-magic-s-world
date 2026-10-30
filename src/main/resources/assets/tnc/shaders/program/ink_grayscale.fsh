#version 150
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;
void main(){vec3 rgb=texture(DiffuseSampler,texCoord).rgb;float gray=dot(rgb,vec3(0.2126,0.7152,0.0722));fragColor=vec4(vec3(gray),1.0);}
