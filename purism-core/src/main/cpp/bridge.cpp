#include <PurismCore.h>
#include <cstdlib>
#include <cstring>
#include <jni.h>
#include <string>
#include <vector>

#define JNI(name) extern "C" JNIEXPORT
#define METHOD(name) Java_com_live2d_sdk_cubism_core_Live2DCubismCoreJNI_##name
static jfieldID field(JNIEnv *e, jobject o, const char *n, const char *s) {
  auto c = e->GetObjectClass(o);
  auto f = e->GetFieldID(c, n, s);
  e->DeleteLocalRef(c);
  return f;
}
static jobject get(JNIEnv *e, jobject o, const char *n, const char *s) {
  return e->GetObjectField(o, field(e, o, n, s));
}
static csmModel *model(JNIEnv *e, jobject o) {
  return reinterpret_cast<csmModel *>(
      e->GetLongField(o, field(e, o, "nativeModelHandle", "J")));
}
static jobject component(JNIEnv *e, jobject o, const char *n,
                         const char *type) {
  std::string sig = "Lcom/live2d/sdk/cubism/core/";
  sig += type;
  sig += ';';
  return get(e, o, n, sig.c_str());
}
static jobject create(JNIEnv *e, jobject o, const char *n, const char *type,
                      int count) {
  std::string path = "com/live2d/sdk/cubism/core/";
  path += type;
  auto c = e->FindClass(path.c_str());
  auto v = e->NewObject(c, e->GetMethodID(c, "<init>", "(I)V"), count);
  e->DeleteLocalRef(c);
  std::string sig = "L" + path + ";";
  e->SetObjectField(o, field(e, o, n, sig.c_str()), v);
  return v;
}
static void floats(JNIEnv *e, jobject o, const char *n, int count,
                   const float *values) {
  auto a = (jfloatArray)get(e, o, n, "[F");
  e->SetFloatArrayRegion(a, 0, count, values);
  e->DeleteLocalRef(a);
}
static void ints(JNIEnv *e, jobject o, const char *n, int count,
                 const int *values) {
  auto a = (jintArray)get(e, o, n, "[I");
  e->SetIntArrayRegion(a, 0, count, values);
  e->DeleteLocalRef(a);
}
static void ids(JNIEnv *e, jobject o, int count, const char **values) {
  auto a = (jobjectArray)get(e, o, "ids", "[Ljava/lang/String;");
  for (int i = 0; i < count; ++i) {
    auto s = e->NewStringUTF(values[i]);
    e->SetObjectArrayElement(a, i, s);
    e->DeleteLocalRef(s);
  }
  e->DeleteLocalRef(a);
}
static void floatRows(JNIEnv *e, jobject o, const char *name, int count,
                      const int *sizes, const float **values,
                      int multiplier = 1) {
  auto a = (jobjectArray)get(e, o, name, "[[F");
  for (int i = 0; i < count; ++i) {
    int size = sizes[i] * multiplier;
    auto row = (jfloatArray)e->GetObjectArrayElement(a, i);
    if (!row || e->GetArrayLength(row) != size) {
      if (row)
        e->DeleteLocalRef(row);
      row = e->NewFloatArray(size);
      e->SetObjectArrayElement(a, i, row);
    }
    e->SetFloatArrayRegion(row, 0, size, values[i]);
    e->DeleteLocalRef(row);
  }
  e->DeleteLocalRef(a);
}
static void updateDrawables(JNIEnv *e, jobject d, csmModel *m) {
  int n = csmGetDrawableCount(m);
  auto flags = (jbyteArray)get(e, d, "dynamicFlags", "[B");
  e->SetByteArrayRegion(flags, 0, n,
                        (const jbyte *)csmGetDrawableDynamicFlags(m));
  e->DeleteLocalRef(flags);
  ints(e, d, "drawOrders", n, csmGetDrawableDrawOrders(m));
  floats(e, d, "opacities", n, csmGetDrawableOpacities(m));
  floatRows(e, d, "vertexPositions", n, csmGetDrawableVertexCounts(m),
            (const float **)csmGetDrawableVertexPositions(m), 2);
  std::vector<int> four(n, 4);
  std::vector<const float *> multiply(n), screen(n);
  auto mc = csmGetDrawableMultiplyColors(m);
  auto sc = csmGetDrawableScreenColors(m);
  for (int i = 0; i < n; ++i) {
    multiply[i] = &mc[i].X;
    screen[i] = &sc[i].X;
  }
  floatRows(e, d, "multiplyColors", n, four.data(), multiply.data());
  floatRows(e, d, "screenColors", n, four.data(), screen.data());
}
static void updateOffscreen(JNIEnv *e, jobject o, csmModel *m) {
  const int n = csmGetOffscreenCount(m);
  floats(e, o, "opacities", n, csmGetOffscreenOpacities(m));
  std::vector<int> sizes(n, 4);
  std::vector<const float *> multiply(n), screen(n);
  auto mc = csmGetOffscreenMultiplyColors(m);
  auto sc = csmGetOffscreenScreenColors(m);
  for (int i = 0; i < n; ++i) {
    multiply[i] = &mc[i].X;
    screen[i] = &sc[i].X;
  }
  floatRows(e, o, "multiplyColors", n, sizes.data(), multiply.data());
  floatRows(e, o, "screenColors", n, sizes.data(), screen.data());
}
JNI(getVersion) jint JNICALL METHOD(getVersion)(JNIEnv *, jclass) {
  return csmGetVersion();
}
JNI(getLatestMocVersion)
jint JNICALL METHOD(getLatestMocVersion)(JNIEnv *, jclass) {
  return csmGetLatestMocVersion();
}
JNI(getMocVersion)
jint JNICALL METHOD(getMocVersion)(JNIEnv *e, jclass, jbyteArray a) {
  auto p = e->GetByteArrayElements(a, nullptr);
  auto r = csmGetMocVersion(p, e->GetArrayLength(a));
  e->ReleaseByteArrayElements(a, p, JNI_ABORT);
  return r;
}
JNI(hasMocConsistency)
jint JNICALL METHOD(hasMocConsistency)(JNIEnv *e, jclass, jbyteArray a) {
  auto p = e->GetByteArrayElements(a, nullptr);
  auto r = csmHasMocConsistency(p, e->GetArrayLength(a));
  e->ReleaseByteArrayElements(a, p, JNI_ABORT);
  return r;
}
JNI(instantiateMoc)
jlong JNICALL METHOD(instantiateMoc)(JNIEnv *e, jclass, jbyteArray a) {
  int n = e->GetArrayLength(a);
  void *p = nullptr;
  if (posix_memalign(&p, csmAlignofMoc, n))
    return 0;
  e->GetByteArrayRegion(a, 0, n, (jbyte *)p);
  if (!csmHasMocConsistency(p, n) || !csmReviveMocInPlace(p, n)) {
    free(p);
    return 0;
  }
  return reinterpret_cast<jlong>(p);
}
JNI(destroyMoc) void JNICALL METHOD(destroyMoc)(JNIEnv *, jclass, jlong h) {
  free(reinterpret_cast<void *>(h));
}
JNI(instantiateModel)
jlong JNICALL METHOD(instantiateModel)(JNIEnv *, jclass, jlong h) {
  auto moc = reinterpret_cast<csmMoc *>(h);
  auto n = csmGetSizeofModel(moc);
  void *p = nullptr;
  if (posix_memalign(&p, csmAlignofModel, n))
    return 0;
  auto m = csmInitializeModelInPlace(moc, p, n);
  if (!m)
    free(p);
  return reinterpret_cast<jlong>(m);
}
JNI(destroyModel) void JNICALL METHOD(destroyModel)(JNIEnv *, jclass, jlong h) {
  free(reinterpret_cast<void *>(h));
}
JNI(updateModel) void JNICALL METHOD(updateModel)(JNIEnv *, jclass, jlong h) {
  csmUpdateModel(reinterpret_cast<csmModel *>(h));
}
JNI(resetDrawableDynamicFlags)
void JNICALL METHOD(resetDrawableDynamicFlags)(JNIEnv *, jclass, jlong h) {
  csmResetDrawableDynamicFlags(reinterpret_cast<csmModel *>(h));
}
JNI(syncToNativeModel)
void JNICALL METHOD(syncToNativeModel)(JNIEnv *e, jclass, jobject o) {
  auto m = model(e, o);
  auto p = component(e, o, "parameters", "CubismParameters");
  auto parts = component(e, o, "parts", "CubismParts");
  auto values = (jfloatArray)get(e, p, "values", "[F");
  e->GetFloatArrayRegion(values, 0, csmGetParameterCount(m),
                         csmGetParameterValues(m));
  e->DeleteLocalRef(values);
  auto opacities = (jfloatArray)get(e, parts, "opacities", "[F");
  e->GetFloatArrayRegion(opacities, 0, csmGetPartCount(m),
                         csmGetPartOpacities(m));
  e->DeleteLocalRef(opacities);
  e->DeleteLocalRef(p);
  e->DeleteLocalRef(parts);
}
JNI(syncFromNativeModel)
void JNICALL METHOD(syncFromNativeModel)(JNIEnv *e, jclass, jobject o) {
  auto m = model(e, o);
  auto p = component(e, o, "parameters", "CubismParameters");
  auto parts = component(e, o, "parts", "CubismParts");
  auto d = component(e, o, "drawables", "CubismDrawables");
  floats(e, p, "values", csmGetParameterCount(m), csmGetParameterValues(m));
  floats(e, parts, "opacities", csmGetPartCount(m), csmGetPartOpacities(m));
  updateDrawables(e, d, m);
  auto orders = component(e, o, "renderOrders", "CubismRenderOrders");
  ints(e, orders, "renderOrders",
       csmGetDrawableCount(m) + csmGetOffscreenCount(m), csmGetRenderOrders(m));
  e->DeleteLocalRef(orders);
  auto offscreen =
      component(e, o, "offscreenRendering", "CubismOffscreenRendering");
  updateOffscreen(e, offscreen, m);
  e->DeleteLocalRef(offscreen);
  e->DeleteLocalRef(p);
  e->DeleteLocalRef(parts);
  e->DeleteLocalRef(d);
}
JNI(initializeJavaModelWithNativeModel)
void JNICALL
    METHOD(initializeJavaModelWithNativeModel)(JNIEnv *e, jclass, jobject o) {
  auto m = model(e, o);
  int n = csmGetParameterCount(m);
  auto p = create(e, o, "parameters", "CubismParameters", n);
  ids(e, p, n, csmGetParameterIds(m));
  floats(e, p, "minimumValues", n, csmGetParameterMinimumValues(m));
  floats(e, p, "maximumValues", n, csmGetParameterMaximumValues(m));
  floats(e, p, "defaultValues", n, csmGetParameterDefaultValues(m));
  floats(e, p, "values", n, csmGetParameterValues(m));
  ints(e, p, "keyCounts", n, csmGetParameterKeyCounts(m));
  floatRows(e, p, "keyValues", n, csmGetParameterKeyCounts(m),
            csmGetParameterKeyValues(m));
  auto types = e->NewIntArray(n), repeats = e->NewIntArray(n);
  e->SetIntArrayRegion(types, 0, n, (const jint *)csmGetParameterTypes(m));
  e->SetIntArrayRegion(repeats, 0, n, csmGetParameterRepeats(m));
  auto pc = e->GetObjectClass(p);
  e->CallVoidMethod(p, e->GetMethodID(pc, "initializeFromJni", "([I[I)V"),
                    types, repeats);
  e->DeleteLocalRef(pc);
  e->DeleteLocalRef(types);
  e->DeleteLocalRef(repeats);
  e->DeleteLocalRef(p);
  n = csmGetPartCount(m);
  auto parts = create(e, o, "parts", "CubismParts", n);
  ids(e, parts, n, csmGetPartIds(m));
  ints(e, parts, "parentPartIndices", n, csmGetPartParentPartIndices(m));
  ints(e, parts, "offscreenIndices", n, csmGetPartOffscreenIndices(m));
  floats(e, parts, "opacities", n, csmGetPartOpacities(m));
  e->DeleteLocalRef(parts);
  n = csmGetDrawableCount(m);
  auto d = create(e, o, "drawables", "CubismDrawables", n);
  ids(e, d, n, csmGetDrawableIds(m));
  ints(e, d, "blendModes", n, csmGetDrawableBlendModes(m));
  auto flags = (jbyteArray)get(e, d, "constantFlags", "[B");
  e->SetByteArrayRegion(flags, 0, n,
                        (const jbyte *)csmGetDrawableConstantFlags(m));
  e->DeleteLocalRef(flags);
  ints(e, d, "textureIndices", n, csmGetDrawableTextureIndices(m));
  ints(e, d, "maskCounts", n, csmGetDrawableMaskCounts(m));
  ints(e, d, "vertexCounts", n, csmGetDrawableVertexCounts(m));
  ints(e, d, "indexCounts", n, csmGetDrawableIndexCounts(m));
  ints(e, d, "parentPartIndices", n, csmGetDrawableParentPartIndices(m));
  floatRows(e, d, "vertexUvs", n, csmGetDrawableVertexCounts(m),
            (const float **)csmGetDrawableVertexUvs(m), 2);
  auto masks = (jobjectArray)get(e, d, "masks", "[[I"),
       indices = (jobjectArray)get(e, d, "indices", "[[S");
  for (int i = 0; i < n; ++i) {
    int count = csmGetDrawableMaskCounts(m)[i];
    auto row = e->NewIntArray(count);
    e->SetIntArrayRegion(row, 0, count, csmGetDrawableMasks(m)[i]);
    e->SetObjectArrayElement(masks, i, row);
    e->DeleteLocalRef(row);
    count = csmGetDrawableIndexCounts(m)[i];
    auto idx = e->NewShortArray(count);
    e->SetShortArrayRegion(idx, 0, count,
                           (const jshort *)csmGetDrawableIndices(m)[i]);
    e->SetObjectArrayElement(indices, i, idx);
    e->DeleteLocalRef(idx);
  }
  e->DeleteLocalRef(masks);
  e->DeleteLocalRef(indices);
  updateDrawables(e, d, m);
  e->DeleteLocalRef(d);
  auto orders = create(e, o, "renderOrders", "CubismRenderOrders",
                       n + csmGetOffscreenCount(m));
  ints(e, orders, "renderOrders", n + csmGetOffscreenCount(m),
       csmGetRenderOrders(m));
  e->DeleteLocalRef(orders);
  n = csmGetOffscreenCount(m);
  auto offscreen =
      create(e, o, "offscreenRendering", "CubismOffscreenRendering", n);
  ints(e, offscreen, "blendModes", n, csmGetOffscreenBlendModes(m));
  ints(e, offscreen, "ownerIndices", n, csmGetOffscreenOwnerIndices(m));
  ints(e, offscreen, "maskCounts", n, csmGetOffscreenMaskCounts(m));
  auto offMasks = (jobjectArray)get(e, offscreen, "masks", "[[I");
  for (int i = 0; i < n; ++i) {
    int count = csmGetOffscreenMaskCounts(m)[i];
    auto row = e->NewIntArray(count);
    e->SetIntArrayRegion(row, 0, count, csmGetOffscreenMasks(m)[i]);
    e->SetObjectArrayElement(offMasks, i, row);
    e->DeleteLocalRef(row);
  }
  e->DeleteLocalRef(offMasks);
  auto offFlags = (jbyteArray)get(e, offscreen, "constantFlags", "[B");
  e->SetByteArrayRegion(offFlags, 0, n,
                        (const jbyte *)csmGetOffscreenConstantFlags(m));
  e->DeleteLocalRef(offFlags);
  updateOffscreen(e, offscreen, m);
  e->DeleteLocalRef(offscreen);
  csmVector2 size, origin;
  float unit;
  csmReadCanvasInfo(m, &size, &origin, &unit);
  auto s = e->NewFloatArray(2), r = e->NewFloatArray(2);
  e->SetFloatArrayRegion(s, 0, 2, &size.X);
  e->SetFloatArrayRegion(r, 0, 2, &origin.X);
  auto cc = e->FindClass("com/live2d/sdk/cubism/core/CubismCanvasInfo");
  auto canvas =
      e->NewObject(cc, e->GetMethodID(cc, "<init>", "([F[FF)V"), s, r, unit);
  e->SetObjectField(o,
                    field(e, o, "canvasInfo",
                          "Lcom/live2d/sdk/cubism/core/CubismCanvasInfo;"),
                    canvas);
  e->DeleteLocalRef(s);
  e->DeleteLocalRef(r);
  e->DeleteLocalRef(cc);
  e->DeleteLocalRef(canvas);
}
