#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler2;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * texelFetch(Sampler2, clamp(UV2 / 16, ivec2(0), ivec2(15)), 0);
    texCoord0 = UV0;
}
