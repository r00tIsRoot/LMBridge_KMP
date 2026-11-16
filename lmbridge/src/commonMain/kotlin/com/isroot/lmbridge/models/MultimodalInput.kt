package com.isroot.lmbridge.models

/**
 * 이미지 인코딩 정책(옵트인). 플랫폼 이미지 타입(Android `Bitmap`, 데스크톱 `BufferedImage`)을
 * `image(...)` 확장으로 넣을 때 적용된다.
 *
 * 기본값 [LOSSLESS_PNG]는 무손실 PNG다. 큰 사진을 그대로 무손실 인코딩하면 수십 MB가 되어
 * 메모리/속도를 낭비하므로, 필요 시 [COMPACT_JPEG]처럼 다운스케일·JPEG 인코딩을 선택한다.
 *
 * @property maxDimension 긴 변 최대 픽셀. 0이면 다운스케일하지 않는다. 이 값을 넘는
 *   이미지만 비율을 유지하며 축소된다(작은 이미지는 그대로).
 * @property format 인코딩 포맷.
 * @property quality 손실 포맷(JPEG) 품질 0..100. PNG에서는 무시된다.
 */
class ImageEncoding(
    val maxDimension: Int = 0,
    val format: Format = Format.PNG,
    val quality: Int = 100,
) {
    enum class Format { PNG, JPEG }

    /** 긴 변이 [maxDimension]을 넘으면 비율을 유지한 축소 크기를, 아니면 null을 돌려준다. */
    internal fun scaledSize(width: Int, height: Int): Pair<Int, Int>? {
        val longest = maxOf(width, height)
        if (maxDimension !in 1 until longest) return null
        val ratio = maxDimension.toFloat() / longest
        return (width * ratio).toInt().coerceAtLeast(1) to (height * ratio).toInt().coerceAtLeast(1)
    }

    companion object {
        /** 무손실 PNG, 다운스케일 없음(기본값). */
        val LOSSLESS_PNG = ImageEncoding()

        /** 긴 변 1024px 다운스케일 + JPEG q90. 온-디바이스 추론에 권장되는 경량값. */
        val COMPACT_JPEG = ImageEncoding(maxDimension = 1024, format = Format.JPEG, quality = 90)
    }
}

/**
 * 멀티모달 입력의 구성 요소.
 *
 * litertlm `Content`를 노출하지 않는다(어댑터 경계). 이미지/오디오는 두 가지 소스로 넣는다:
 * - **바이트**([ImageBytes]/[AudioBytes]) — 이미 인코딩된 미디어 바이트(재인코딩 없이 그대로 전달).
 * - **파일 경로**([ImageFile]/[AudioFile]) — 파일을 메모리에 올리지 않고 litertlm에 경로로 전달.
 *
 * 플랫폼 이미지 타입은 `MultimodalInput.Builder.image(...)` 플랫폼 확장이 [ImageBytes]로 인코딩해 넣는다.
 */
sealed class MultimodalContent {
    data class Text(val text: String) : MultimodalContent()

    /** 이미 인코딩된 이미지 바이트(JPEG/PNG/WebP 등). 재인코딩 없이 그대로 전달한다. */
    data class ImageBytes(val bytes: ByteArray) : MultimodalContent() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ImageBytes && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** 로컬 이미지 파일 경로. 메모리 적재 없이 경로로 전달한다. */
    data class ImageFile(val path: String) : MultimodalContent()

    /** 이미 인코딩된 오디오 바이트. 재인코딩 없이 그대로 전달한다. */
    data class AudioBytes(val bytes: ByteArray) : MultimodalContent() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is AudioBytes && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** 로컬 오디오 파일 경로. 메모리 적재 없이 경로로 전달한다. */
    data class AudioFile(val path: String) : MultimodalContent()

    /**
     * 로컬 텍스트 문서 파일. 내용을 컨텍스트로 주입한다.
     *
     * 텍스트 문서만 지원한다(PDF·오피스 등 바이너리 문서는 오류로 표면화된다).
     * @property charset 파일을 디코드할 문자셋 이름(예: "UTF-8", "EUC-KR"). 기본 UTF-8.
     */
    data class Document(
        val path: String,
        val charset: String = "UTF-8",
    ) : MultimodalContent()
}

/**
 * 멀티모달 입력. [parts]의 순서가 모델에 전달되는 순서다.
 *
 * 여러 modality(텍스트·이미지·오디오·문서)를 **혼합**하여 전달할 수 있다.
 */
data class MultimodalInput(
    val parts: List<MultimodalContent>,
) {
    class Builder {
        private val parts = mutableListOf<MultimodalContent>()

        /** 구성 요소를 그대로 추가한다(플랫폼 확장이 이미지 등을 변환해 넣을 때 쓴다). */
        fun part(content: MultimodalContent) = apply { parts.add(content) }

        fun text(text: String) = part(MultimodalContent.Text(text))

        /** 이미 인코딩된 이미지 바이트를 그대로 추가한다. */
        fun imageBytes(bytes: ByteArray) = part(MultimodalContent.ImageBytes(bytes))

        /** 이미지 파일 경로를 추가한다(메모리 적재 없음). */
        fun imageFile(path: String) = part(MultimodalContent.ImageFile(path))

        /** 이미 인코딩된 오디오 바이트를 그대로 추가한다. */
        fun audioBytes(bytes: ByteArray) = part(MultimodalContent.AudioBytes(bytes))

        /** 오디오 파일 경로를 추가한다(메모리 적재 없음). */
        fun audioFile(path: String) = part(MultimodalContent.AudioFile(path))

        /** 텍스트 문서 파일. 기본 UTF-8로 디코드하여 내용을 주입한다. */
        fun document(path: String, charset: String = "UTF-8") =
            part(MultimodalContent.Document(path, charset))

        fun build() = MultimodalInput(parts.toList())
    }

    companion object {
        fun text(prompt: String) = MultimodalInput(listOf(MultimodalContent.Text(prompt)))

        fun textWithContent(instruction: String, content: String) = MultimodalInput(
            listOf(
                MultimodalContent.Text(instruction),
                MultimodalContent.Text(content),
            ),
        )
    }
}
