    // Each GL owner compiles only the variants it actually draws. Keep upstream
    // indices, shader sources and uniforms so masks/offscreen blends stay intact.
    private void generateShaders() {
        if (!shaderSets.isEmpty()) return;
        for (int i = 0; i < CubismShaderIndexConstants.SHADER_COUNT; i++) {
            shaderSets.add(new CubismShaderSet());
        }
    }

    private CubismShaderSet getShaderSet(int requestedIndex) {
        generateShaders();
        final int normalBase = CubismShaderIndexConstants.NORMAL_OVER_BASE_INDEX;
        final int blendBase = CubismShaderIndexConstants.BLEND_MODE_START_INDEX;
        final int variants = CubismShaderIndexConstants.MASK_TYPE_COUNT;
        // Legacy additive/multiply modes use the same programs as normal mode.
        final int index = requestedIndex >= normalBase && requestedIndex < blendBase
            ? normalBase + (requestedIndex - normalBase) % variants : requestedIndex;
        CubismShaderSet shader = shaderSets.get(index);
        if (shader.shaderProgram != 0) return shader;

        String vertex;
        String fragment;
        CubismShaderIndexFactors.ColorBlendMode color = null;
        CubismShaderIndexFactors.AlphaBlendMode alpha = null;
        CubismShaderIndexFactors.MaskType mask = CubismShaderIndexFactors.MaskType.MASKED;
        if (index < normalBase) {
            boolean copy = index == CubismShaderIndexFactors.UtilityShaderType.COPY.index;
            vertex = copy ? "VertShaderSrcCopy" : "VertShaderSrcSetupMask";
            fragment = copy ? "FragShaderSrcCopy" : "FragShaderSrcSetupMask";
        } else {
            final boolean advanced = index >= blendBase;
            final int variant = (index - (advanced ? blendBase : normalBase)) % variants;
            final int masking = variant % 3;
            final boolean premultiplied = variant >= 3;
            mask = CubismShaderIndexFactors.MaskType.values()[variant];
            vertex = "VertShaderSrc" + (masking == 0 ? "" : "Masked") + (advanced ? "Blend" : "");
            fragment = "FragShaderSrc" + (masking == 0 ? "" : masking == 1 ? "Mask" : "MaskInverted")
                + (premultiplied ? "PremultipliedAlpha" : "") + (advanced ? "Blend" : "");
            if (advanced) {
                // Normal/Over is supplied by the legacy shaders and is skipped
                // in R5's advanced shader index space.
                final int combination = (index - blendBase) / variants + 1;
                color = CubismShaderIndexFactors.ColorBlendMode.values()[combination / CubismShaderIndexConstants.ALPHA_BLEND_COUNT];
                alpha = CubismShaderIndexFactors.AlphaBlendMode.values()[combination % CubismShaderIndexConstants.ALPHA_BLEND_COUNT];
            }
        }
        shader.shaderProgram = loadShaderProgramFromFile(vertex + ".vert",
            fragment + (EXT_MODE ? "Tegra" : "") + ".frag", color, alpha);
        if (shader.shaderProgram == 0) {
            throw new IllegalStateException("Cannot create Cubism shader " + index);
        }
        setShaderSet(shader, mask, index >= blendBase);
        return shader;
    }
