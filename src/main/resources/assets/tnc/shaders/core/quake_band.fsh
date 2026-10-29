#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    // No alpha discard: soft edges and the complete ten-tick fade remain continuous.
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
