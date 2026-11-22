// js/core/water.js
// 水面背景：全局单例，只初始化一次
// 夜色海面 + 月光反射：Gerstner 波、解析法线、菲涅尔、月光高光、远处雾化

let waterInited = false;

function initWater(containerId) {
    // ---- 防重复初始化 ----
    if (waterInited) {
        console.log('[water] 已初始化，跳过');
        return;
    }

    const container = document.getElementById(containerId || 'canvas-container');
    if (!container) {
        console.warn('[water] 找不到容器:', containerId);
        return;
    }

    let scene, camera, renderer, waterMesh, clock;

    scene = new THREE.Scene();

    // 相机贴近海面、略微仰视，让海平面落在画面下方
    camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 600);
    camera.position.set(0, 3.2, 10);
    camera.lookAt(0, 15, -50);

    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setClearColor(0x000000, 0);   // 透明，露出纯黑背景
    container.appendChild(renderer.domElement);
    clock = new THREE.Clock();

    const vertexShader = `
    uniform float time;
    uniform float uSpeed;
    varying vec3 vWorldPos;
    varying vec3 vNormal;

    #define PI 3.14159265359

    // Gerstner 波：水平位移 + 垂直起伏，同时累加切线/副切线用于解析法线
    vec3 gerstner(vec2 p, vec2 dir, float steepness, float wavelength, float t,
                  inout vec3 tangent, inout vec3 binormal) {
        float k = 2.0 * PI / wavelength;
        vec2 d = normalize(dir);
        float c = sqrt(9.8 / k);
        float f = k * (dot(d, p) - c * t);
        float a = steepness / k;
        float sf = sin(f);
        float cf = cos(f);

        tangent += vec3(
            -d.x * d.x * steepness * sf,
             d.x * steepness * cf,
            -d.x * d.y * steepness * sf
        );
        binormal += vec3(
            -d.x * d.y * steepness * sf,
             d.y * steepness * cf,
            -d.y * d.y * steepness * sf
        );

        return vec3(d.x * a * cf, a * sf, d.y * a * cf);
    }

    void main() {
        vec2 p = position.xz;
        float t = time * uSpeed;

        vec3 tangent  = vec3(1.0, 0.0, 0.0);
        vec3 binormal = vec3(0.0, 0.0, 1.0);

        vec3 disp = vec3(0.0);
        disp += gerstner(p, vec2(1.0, 0.25), 0.28, 44.0, t * 0.28, tangent, binormal);
        disp += gerstner(p, vec2(0.7, -0.7), 0.24, 24.0, t * 0.40, tangent, binormal);
        disp += gerstner(p, vec2(-0.4, 1.0), 0.16, 12.0, t * 0.55, tangent, binormal);
        disp += gerstner(p, vec2(0.9, 0.6),  0.10, 5.5,  t * 0.75, tangent, binormal);

        vec3 pos = position + disp;

        vWorldPos = (modelMatrix * vec4(pos, 1.0)).xyz;
        vNormal = normalize((modelMatrix * vec4(normalize(cross(binormal, tangent)), 0.0)).xyz);

        gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(pos, 1.0);
    }`;

    const fragmentShader = `
    uniform float time;
    varying vec3 vWorldPos;
    varying vec3 vNormal;

    float hash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }

    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                   mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
    }

    void main() {
        vec3 N = normalize(vNormal);
        vec3 V = normalize(cameraPosition - vWorldPos);
        // 固定光源方向（只用于高光计算，不与任何月亮/天空绑定）
        vec3 L = normalize(vec3(0.0, 0.45, -1.0));
        vec3 H = normalize(L + V);
        float dist = length(cameraPosition - vWorldPos);

        float ndv = clamp(dot(N, V), 0.0, 1.0);
        float fresnel = pow(1.0 - ndv, 4.0);

        float ndh = max(dot(N, H), 0.0);
        float ndl = max(dot(N, L), 0.0);
        float spec = pow(ndh, 260.0);   // 尖锐的月光倒影
        float sheen = pow(ndh, 10.0);   // 大面积的月光铺陈

        // 深海底色，浪峰略微提亮
        float crest = smoothstep(-0.6, 1.1, vWorldPos.y);
        vec3 deep = vec3(0.004, 0.007, 0.012);
        vec3 shallow = vec3(0.014, 0.026, 0.040);
        vec3 color = mix(deep, shallow, crest);

        // 菲涅尔天空反射（暗蓝灰）
        vec3 sky = vec3(0.050, 0.070, 0.095);
        color = mix(color, sky, fresnel * 0.55);

        // 浪面受光：朝向月亮的一面 + 朝上的浪峰，让起伏在暗部也能看出来
        color += vec3(0.035, 0.048, 0.065) * ndl * 0.50;
        color += vec3(0.016, 0.024, 0.036) * clamp(N.y, 0.0, 1.0) * 0.15;

        // 镜头正下方的高光压暗，让月光反射集中在画面中段（同时避免近处过曝）
        float nearFade = smoothstep(2.0, 12.0, dist);

        // 月光高光
        vec3 moonColor = vec3(0.86, 0.92, 1.0);
        color += moonColor * spec * 2.0 * nearFade;
        color += moonColor * sheen * 0.07 * (0.35 + 0.65 * nearFade);

        // 浪尖碎光：只在靠近月光反射带的位置出现
        float glint = smoothstep(0.978, 1.0,
                noise(vWorldPos.xz * 7.0 + vec2(time * 0.25, time * 0.15)));
        color += moonColor * glint * pow(ndh, 3.0) * 0.7 * nearFade;

        // 远处淡出，和夜空融合
        color = mix(color, vec3(0.002, 0.004, 0.007), smoothstep(30.0, 120.0, dist));

        gl_FragColor = vec4(color, 1.0);
    }`;

    const waterMaterial = new THREE.ShaderMaterial({
        vertexShader,
        fragmentShader,
        uniforms: {
            time: { value: 0 },
            uSpeed: { value: 2.4 }
        }
    });

    const waterGeometry = new THREE.PlaneGeometry(200, 200, 300, 300);
    waterGeometry.rotateX(-Math.PI / 2);   // 直接铺在 XZ 平面上，shader 内按世界坐标算波
    waterMesh = new THREE.Mesh(waterGeometry, waterMaterial);
    scene.add(waterMesh);

    window.addEventListener('resize', onWindowResize);
    animate();

    function animate() {
        requestAnimationFrame(animate);
        const elapsedTime = clock.getElapsedTime();
        waterMesh.material.uniforms.time.value = elapsedTime;
        renderer.render(scene, camera);
    }

    function onWindowResize() {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    }

    // ---- 标记已初始化 ----
    waterInited = true;
    console.log('[water] 初始化完成');
}

