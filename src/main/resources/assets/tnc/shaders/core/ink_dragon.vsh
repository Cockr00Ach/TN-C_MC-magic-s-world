#version 150
in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float WaveTime;
uniform float FlightAmount;
out vec4 vertexColor;
void main() {
    vec3 p=Position;
    float weight=smoothstep(-4.0,8.0,p.x)*FlightAmount;
    float phase=(p.x+4.0)*.17-WaveTime*.085;
    float amplitude=(1.2+1.6*smoothstep(8.0,62.0,p.x))*weight;
    p.z+=sin(phase)*amplitude;
    p.y+=sin(phase)*1.4*weight;
    gl_Position=ProjMat*ModelViewMat*vec4(p,1.0);
    vertexColor=Color;
}
