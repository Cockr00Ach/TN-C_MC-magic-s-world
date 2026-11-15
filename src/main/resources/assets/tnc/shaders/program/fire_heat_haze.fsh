#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float HazeTime;
uniform float CenterX0, CenterY0, RadiusX0, RadiusY0, Strength0;
uniform float CenterX1, CenterY1, RadiusX1, RadiusY1, Strength1;
uniform float CenterX2, CenterY2, RadiusX2, RadiusY2, Strength2;
uniform float CenterX3, CenterY3, RadiusX3, RadiusY3, Strength3;

in vec2 texCoord;
out vec4 fragColor;

vec2 localHeat(vec2 center, vec2 radius, float strength) {
    if (strength <= 0.0 || min(radius.x, radius.y) <= 0.0) return vec2(0.0);
    vec2 local = (texCoord - center) / radius;
    float distance = length(local);
    if (distance >= 1.0) return vec2(0.0);
    float envelope = 1.0 - smoothstep(0.12, 1.0, distance);
    // Two incommensurate, upward-running ripples; no chromatic shift or color wash.
    float a = sin(local.y * 17.0 - HazeTime * 7.0 + local.x * 4.0);
    float b = sin(local.y * 29.0 - HazeTime * 11.0 - local.x * 13.0);
    return vec2(a * 0.74 + b * 0.26, cos(local.x * 16.0 + HazeTime * 5.0) * 0.25)
            * envelope * clamp(strength, 0.0, 1.0) * 2.0;
}

void main() {
    vec2 pixels = localHeat(vec2(CenterX0, CenterY0), vec2(RadiusX0, RadiusY0), Strength0)
                + localHeat(vec2(CenterX1, CenterY1), vec2(RadiusX1, RadiusY1), Strength1)
                + localHeat(vec2(CenterX2, CenterY2), vec2(RadiusX2, RadiusY2), Strength2)
                + localHeat(vec2(CenterX3, CenterY3), vec2(RadiusX3, RadiusY3), Strength3);
    if (dot(pixels, pixels) == 0.0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }
    // Overlapping masks cannot exceed two actual framebuffer pixels in any direction.
    pixels *= min(1.0, 2.0 / max(length(pixels), 0.0001));
    vec2 halfTexel = 0.5 / InSize;
    vec2 uv = clamp(texCoord + pixels / InSize, halfTexel, vec2(1.0) - halfTexel);
    fragColor = texture(DiffuseSampler, uv);
}
