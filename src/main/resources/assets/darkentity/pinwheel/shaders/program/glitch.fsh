uniform sampler2D DiffuseSampler0;
uniform float VeilRenderTime;

in vec2 texCoord;
out vec4 fragColor;

float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    float tick = mod(floor(VeilRenderTime * 12.0), 1000.0);

    // горизонтальные сдвиги строк
    float line = floor(uv.y * 38.0);
    float n = rand(vec2(line, tick));
    float shift = 0.0;
    if (n > 0.94) {
        shift = (rand(vec2(line, tick + 7.0)) - 0.5) * 0.14;
    }

    // блоки-артефакты
    float block = floor(uv.x * 10.0) + floor(uv.y * 6.0) * 10.0;
    if (rand(vec2(block, tick)) > 0.985) {
        uv.y = fract(uv.y + 0.08);
    }

    // хроматическая аберрация
    vec2 g = vec2(uv.x + shift, uv.y);
    float ab = 0.004 + (n > 0.97 ? 0.012 : 0.0);
    vec3 col;
    col.r = texture(DiffuseSampler0, g + vec2(ab, 0.0)).r;
    col.g = texture(DiffuseSampler0, g).g;
    col.b = texture(DiffuseSampler0, g - vec2(ab, 0.0)).b;

    // сканлайны и виньетка
    float scan = 0.94 + 0.06 * sin(uv.y * 900.0);
    float vig = smoothstep(0.95, 0.25, distance(uv, vec2(0.5)));
    col *= scan * mix(0.7, 1.0, vig);

    fragColor = vec4(col, 1.0);
}
