cmake_minimum_required(VERSION 3.26)
project(pocket_nemotron LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
if(NOT NEMO_SOURCE OR NOT EXISTS "${NEMO_SOURCE}/src/asr/diar/diar_pipeline.cpp")
  message(FATAL_ERROR "Set NEMO_SOURCE to the pinned NeMo-Speech.cpp source")
endif()
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
# Use the upstream patch series without modifying the pinned source checkout.
execute_process(COMMAND "${CMAKE_COMMAND}"
  "-DSOURCE_DIR=${NEMO_SOURCE}/llama.cpp" "-DPATCH_DIR=${NEMO_SOURCE}/patches"
  "-DDEST_DIR=${CMAKE_BINARY_DIR}/patched-llama" "-P" "${NEMO_SOURCE}/cmake/llama_cpp.cmake"
  COMMAND_ERROR_IS_FATAL ANY)
set(NEMO_SPEECH_LLAMA_CPP_DIR "${CMAKE_BINARY_DIR}/patched-llama")
add_subdirectory("${NEMO_SPEECH_LLAMA_CPP_DIR}/ggml" "ggml" EXCLUDE_FROM_ALL)
file(GLOB runtime_sources "${NEMO_SOURCE}/src/runtime/ggml/*.cpp")
set(sources ${runtime_sources}
  "${NEMO_SOURCE}/src/asr/encoder/fastconformer.cpp"
  "${NEMO_SOURCE}/src/asr/encoder/rope_transformer.cpp"
  "${NEMO_SOURCE}/src/asr/encoder/transformer_encoder.cpp"
  "${NEMO_SOURCE}/src/asr/encoder/rel_pos_attention.cpp"
  "${NEMO_SPEECH_LLAMA_CPP_DIR}/src/llama-mmap.cpp"
  "${NEMO_SOURCE}/src/common/parameter_parser.cpp"
  "${NEMO_SOURCE}/src/asr/features/fe.cpp"
  "${NEMO_SOURCE}/src/asr/diar/sortformer_model.cpp"
  "${NEMO_SOURCE}/src/asr/diar/aosc_state.cpp"
  "${NEMO_SOURCE}/src/asr/diar/diar_pipeline.cpp")
if(ANDROID)
  add_library(pocket_nemotron SHARED ${sources} nemotron_jni.cpp)
  target_link_options(pocket_nemotron PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,-Bsymbolic" "-Wl,-z,max-page-size=16384")
else()
  find_package(JNI REQUIRED COMPONENTS JVM)
  add_library(pocket_nemotron SHARED ${sources} nemotron_jni.cpp)
  target_include_directories(pocket_nemotron PRIVATE ${JNI_INCLUDE_DIRS})
  target_link_options(pocket_nemotron PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,-Bsymbolic")
endif()
target_include_directories(pocket_nemotron PRIVATE
  "${NEMO_SOURCE}/src/runtime/ggml" "${NEMO_SOURCE}/src/common"
  "${NEMO_SOURCE}/src/asr" "${NEMO_SOURCE}/src/asr/encoder"
  "${NEMO_SOURCE}/src/asr/features" "${NEMO_SOURCE}/src/asr/diar"
  "${NEMO_SPEECH_LLAMA_CPP_DIR}/src" "${NEMO_SPEECH_LLAMA_CPP_DIR}/include")
target_compile_definitions(pocket_nemotron PRIVATE NEMO_SPEECH_GGML_PATCHED=1)
target_link_libraries(pocket_nemotron PRIVATE ggml ggml-base ggml-cpu)
