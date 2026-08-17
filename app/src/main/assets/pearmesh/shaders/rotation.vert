#version 300 es

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform float uTime;
uniform vec2 uViewScale;
uniform vec3 uImageScales;
uniform int uInstance;
uniform bool uArtworkFill;

out vec2 vTexCoord;

vec2 rotateClockwise(vec2 value, float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return vec2(
        cosine * value.x + sine * value.y,
        -sine * value.x + cosine * value.y
    );
}

void main() {
    if (uArtworkFill) {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTexCoord = (aTexCoord - 0.5) / uViewScale + 0.5;
        return;
    }

    vec2 translation = vec2(0.0);
    float timeScale = 120.0;
    float imageScale = uImageScales.x;
    if (uInstance == 1) {
        translation = vec2(-0.5, 0.7);
        timeScale = 90.0;
        imageScale = uImageScales.y;
    } else if (uInstance == 2) {
        translation = vec2(-0.95, -0.7);
        timeScale = 70.0;
        imageScale = uImageScales.z;
    }

    float angle = uTime * 6.283185307179586 / timeScale;
    vec2 position = aPosition * imageScale;
    position = rotateClockwise(position, angle);
    position += translation;
    position = rotateClockwise(position, angle);
    position *= uViewScale;

    gl_Position = vec4(position, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
