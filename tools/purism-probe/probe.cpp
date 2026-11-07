#include <PurismCore.h>
#include <dlfcn.h>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

// Resolve each implementation in its own handle: never interpose Core symbols.
struct Core {
    void *library;
#define API(name) decltype(&name) name = nullptr
    API(csmGetVersion); API(csmHasMocConsistency); API(csmReviveMocInPlace);
    API(csmGetSizeofModel); API(csmInitializeModelInPlace); API(csmUpdateModel);
    API(csmGetParameterCount); API(csmGetParameterIds); API(csmGetParameterValues);
    API(csmGetParameterMinimumValues); API(csmGetParameterMaximumValues);
    API(csmGetParameterDefaultValues); API(csmGetPartCount); API(csmGetPartIds);
    API(csmGetPartOpacities); API(csmGetDrawableCount); API(csmGetDrawableIds);
    API(csmGetDrawableVertexCounts); API(csmGetDrawableVertexPositions);
    API(csmGetDrawableVertexUvs); API(csmGetDrawableIndexCounts);
    API(csmGetDrawableIndices); API(csmGetDrawableOpacities);
    API(csmGetDrawableRenderOrders); API(csmGetDrawableTextureIndices);
    API(csmGetDrawableMaskCounts); API(csmGetDrawableMasks);
    API(csmGetDrawableConstantFlags); API(csmGetDrawableMultiplyColors);
    API(csmGetDrawableScreenColors); API(csmResetDrawableDynamicFlags);
#undef API
    explicit Core(const char *path) : library(dlopen(path, RTLD_NOW | RTLD_LOCAL)) {
        if (!library) throw std::runtime_error(dlerror());
#define LOAD(name) name = reinterpret_cast<decltype(name)>(dlsym(library, #name)); \
    if (!name) throw std::runtime_error("Missing symbol: " #name)
        LOAD(csmGetVersion); LOAD(csmHasMocConsistency); LOAD(csmReviveMocInPlace);
        LOAD(csmGetSizeofModel); LOAD(csmInitializeModelInPlace); LOAD(csmUpdateModel);
        LOAD(csmGetParameterCount); LOAD(csmGetParameterIds); LOAD(csmGetParameterValues);
        LOAD(csmGetParameterMinimumValues); LOAD(csmGetParameterMaximumValues);
        LOAD(csmGetParameterDefaultValues); LOAD(csmGetPartCount); LOAD(csmGetPartIds);
        LOAD(csmGetPartOpacities); LOAD(csmGetDrawableCount); LOAD(csmGetDrawableIds);
        LOAD(csmGetDrawableVertexCounts); LOAD(csmGetDrawableVertexPositions);
        LOAD(csmGetDrawableVertexUvs); LOAD(csmGetDrawableIndexCounts);
        LOAD(csmGetDrawableIndices); LOAD(csmGetDrawableOpacities);
        LOAD(csmGetDrawableRenderOrders); LOAD(csmGetDrawableTextureIndices);
        LOAD(csmGetDrawableMaskCounts); LOAD(csmGetDrawableMasks);
        LOAD(csmGetDrawableConstantFlags); LOAD(csmGetDrawableMultiplyColors);
        LOAD(csmGetDrawableScreenColors); LOAD(csmResetDrawableDynamicFlags);
#undef LOAD
    }
    ~Core() { if (library) dlclose(library); }
};

struct Model {
    Core &core;
    void *mocMemory = nullptr;
    void *modelMemory = nullptr;
    csmModel *model = nullptr;
    Model(Core &core, const char *path) : core(core) {
        std::ifstream file(path, std::ios::binary | std::ios::ate);
        if (!file) throw std::runtime_error("Cannot read MOC3");
        const auto length = file.tellg();
        if (length <= 0 || length > 128 * 1024 * 1024) throw std::runtime_error("Invalid MOC3 size");
        const auto size = static_cast<unsigned int>(length);
        if (posix_memalign(&mocMemory, csmAlignofMoc, size)) throw std::bad_alloc();
        file.seekg(0);
        file.read(static_cast<char *>(mocMemory), size);
        if (!file || !core.csmHasMocConsistency(mocMemory, size)) {
            free(mocMemory); mocMemory = nullptr;
            throw std::runtime_error("MOC3 validation failed");
        }
        auto *moc = core.csmReviveMocInPlace(mocMemory, size);
        if (!moc) { free(mocMemory); mocMemory = nullptr; throw std::runtime_error("MOC3 revive failed"); }
        const auto modelSize = core.csmGetSizeofModel(moc);
        if (posix_memalign(&modelMemory, csmAlignofModel, modelSize)) {
            free(mocMemory); mocMemory = nullptr; throw std::bad_alloc();
        }
        model = core.csmInitializeModelInPlace(moc, modelMemory, modelSize);
        if (!model) {
            free(modelMemory); free(mocMemory); modelMemory = mocMemory = nullptr;
            throw std::runtime_error("Model initialization failed");
        }
    }
    ~Model() { free(modelMemory); free(mocMemory); }
    void reset() {
        const int count = core.csmGetParameterCount(model);
        std::memcpy(core.csmGetParameterValues(model), core.csmGetParameterDefaultValues(model), count * sizeof(float));
        for (int i = 0; i < core.csmGetPartCount(model); ++i) core.csmGetPartOpacities(model)[i] = 1;
    }
    bool set(const std::string &target, const std::string &id, float value) {
        const bool parameter = target == "Parameter";
        if (!parameter && target != "PartOpacity") throw std::runtime_error("Unsupported trajectory target");
        const int count = parameter ? core.csmGetParameterCount(model) : core.csmGetPartCount(model);
        auto ids = parameter ? core.csmGetParameterIds(model) : core.csmGetPartIds(model);
        auto values = parameter ? core.csmGetParameterValues(model) : core.csmGetPartOpacities(model);
        for (int i = 0; i < count; ++i) if (id == ids[i]) { values[i] = value; return true; }
        return false;
    }
};

static void require(bool ok, const char *what) { if (!ok) throw std::runtime_error(what); }

struct Comparison {
    double maxVertex = 0, maxOther = 0;
    size_t vertices = 0, differences = 0;
    void number(float a, float b, bool vertex = false) {
        require(std::isfinite(a) && std::isfinite(b), "Non-finite output");
        const double delta = std::abs(double(a) - double(b));
        (vertex ? maxVertex : maxOther) = std::max(vertex ? maxVertex : maxOther, delta);
        if (delta > 0.0001 + 0.0001 * std::max(std::abs(a), std::abs(b))) ++differences;
    }
    void compare(Model &a, Model &b) {
        auto &x = a.core; auto &y = b.core;
        auto *m = a.model; auto *n = b.model;
        const int count = x.csmGetDrawableCount(m);
        require(count == y.csmGetDrawableCount(n), "Drawable count mismatch");
        require(x.csmGetParameterCount(m) == y.csmGetParameterCount(n), "Parameter count mismatch");
        require(x.csmGetPartCount(m) == y.csmGetPartCount(n), "Part count mismatch");
        for (int i = 0; i < x.csmGetParameterCount(m); ++i) {
            require(!strcmp(x.csmGetParameterIds(m)[i], y.csmGetParameterIds(n)[i]), "Parameter ID mismatch");
            number(x.csmGetParameterMinimumValues(m)[i], y.csmGetParameterMinimumValues(n)[i]);
            number(x.csmGetParameterMaximumValues(m)[i], y.csmGetParameterMaximumValues(n)[i]);
            number(x.csmGetParameterDefaultValues(m)[i], y.csmGetParameterDefaultValues(n)[i]);
        }
        for (int i = 0; i < count; ++i) {
            require(!strcmp(x.csmGetDrawableIds(m)[i], y.csmGetDrawableIds(n)[i]), "Drawable ID mismatch");
#define EXACT(name) require(x.name(m)[i] == y.name(n)[i], #name " mismatch")
            EXACT(csmGetDrawableVertexCounts); EXACT(csmGetDrawableIndexCounts);
            EXACT(csmGetDrawableRenderOrders); EXACT(csmGetDrawableTextureIndices);
            EXACT(csmGetDrawableMaskCounts); EXACT(csmGetDrawableConstantFlags);
#undef EXACT
            number(x.csmGetDrawableOpacities(m)[i], y.csmGetDrawableOpacities(n)[i]);
            const auto ac = x.csmGetDrawableMultiplyColors(m)[i], bc = y.csmGetDrawableMultiplyColors(n)[i];
            number(ac.X, bc.X); number(ac.Y, bc.Y); number(ac.Z, bc.Z); number(ac.W, bc.W);
            const auto as = x.csmGetDrawableScreenColors(m)[i], bs = y.csmGetDrawableScreenColors(n)[i];
            number(as.X, bs.X); number(as.Y, bs.Y); number(as.Z, bs.Z); number(as.W, bs.W);
            for (int j = 0; j < x.csmGetDrawableVertexCounts(m)[i]; ++j) {
                const auto av = x.csmGetDrawableVertexPositions(m)[i][j], bv = y.csmGetDrawableVertexPositions(n)[i][j];
                number(av.X, bv.X, true); number(av.Y, bv.Y, true); ++vertices;
                const auto au = x.csmGetDrawableVertexUvs(m)[i][j], bu = y.csmGetDrawableVertexUvs(n)[i][j];
                number(au.X, bu.X); number(au.Y, bu.Y);
            }
            for (int j = 0; j < x.csmGetDrawableIndexCounts(m)[i]; ++j)
                require(x.csmGetDrawableIndices(m)[i][j] == y.csmGetDrawableIndices(n)[i][j], "Triangle mismatch");
            for (int j = 0; j < x.csmGetDrawableMaskCounts(m)[i]; ++j)
                require(x.csmGetDrawableMasks(m)[i][j] == y.csmGetDrawableMasks(n)[i][j], "Mask mismatch");
        }
    }
};

int main(int argc, char **argv) {
    if (argc != 5) { std::fprintf(stderr, "Usage: core-probe official.so purism.so model.moc3 trajectory.txt\n"); return 2; }
    try {
        Core official(argv[1]), purism(argv[2]);
        std::fprintf(stderr, "Loading official Core model\n");
        Model a(official, argv[3]);
        std::fprintf(stderr, "Loading Purism Core model\n");
        Model b(purism, argv[3]);
        Comparison result;
        std::ifstream input(argv[4]);
        require(bool(input), "Cannot read trajectory");
        std::string line;
        size_t frames = 0, unmatched = 0;
        while (std::getline(input, line)) {
            if (line == "FRAME") { a.reset(); b.reset(); }
            else if (line == "END") {
                official.csmUpdateModel(a.model); purism.csmUpdateModel(b.model);
                result.compare(a, b); ++frames;
                official.csmResetDrawableDynamicFlags(a.model); purism.csmResetDrawableDynamicFlags(b.model);
            } else {
                std::istringstream row(line);
                std::string target, id; double value;
                require(bool(row >> target >> id >> value) && std::isfinite(value), "Invalid trajectory");
                const bool found = a.set(target, id, value);
                require(found == b.set(target, id, value), "Motion target mismatch");
                if (!found) ++unmatched;
            }
        }
        require(frames > 0 && result.vertices > 0, "Empty comparison");
        std::printf("{\"officialVersion\":%u,\"purismVersion\":%u,\"frames\":%zu,\"vertices\":%zu,\"maxVertexDelta\":%.9g,\"maxOtherDelta\":%.9g,\"differences\":%zu,\"unmatchedTargets\":%zu}\n",
            official.csmGetVersion(), purism.csmGetVersion(), frames, result.vertices,
            result.maxVertex, result.maxOther, result.differences, unmatched);
        return result.differences ? 1 : 0;
    } catch (const std::exception &e) { std::fprintf(stderr, "%s\n", e.what()); return 2; }
}
