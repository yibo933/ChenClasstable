package com.chen.classtable.registry

/** 学校/教务系统 → 解析器类别（业务配置）。 */
object ImportTypeRegistry {

    enum class Kind { ZF, NEW_ZF, JZ, QZ, URP, QG, SW, KINGOSOFT, EAMS, THU, GENERIC }

    /** 反编译版 158 个 importType（供后续扩展与自检）。 */
    val ALL_IMPORT_TYPES = arrayOf(
        "ahmu", "ahnu", "aic", "bfa", "bfa_post", "bjtu", "bnuz", "buaa", "ccibe",
        "ccsu_qz_old", "ccut_qz_old", "cdut", "cf", "cf_new", "changzhou", "chaoxing_share",
        "cidp", "cityu", "cnu", "cppu", "cqu", "cqupt", "csv", "ctgu", "cumtb", "cupl_post",
        "dhu", "ecjtu", "ecupl", "fdu", "fstvc", "gdbh", "gdbyxy", "gdei", "gliet", "gxic",
        "gxnu", "gzhuyjs", "hbmzu", "hbut", "hfu", "hit", "hitsz", "hniu", "hnjm", "hnust",
        "hqu", "hrbeu_post", "huat", "hunnu_shuwei", "hust", "javtc", "jlict_qz_old",
        "jlu_post", "jmpt", "jmu", "jmucy", "jnu", "jxau", "jxnu", "jz", "jz_1", "jz_x",
        "kg_zx", "kingo_new", "kingosoft", "lngd", "login_chaoxing", "login_xbellsoft", "lz",
        "nau", "nfu", "nju", "nnutc", "nua", "nuaajc", "nuist", "nwpu", "nwpu_post", "nyist",
        "pku", "qmu", "qz", "qz_2017", "qz_2024", "qz_ahut", "qz_bjfu", "qz_br", "qz_crazy",
        "qz_ecust", "qz_fspt", "qz_njust", "qz_old", "qz_single_node", "qz_ustb", "qz_wit",
        "qz_with_node", "ruc", "scau", "sdpei", "sdu", "seig", "seu_2017", "shcc", "shsmu",
        "shtu_post", "shtu_post_2024", "shu", "shu2024", "shuwei_json", "shuwei_m",
        "shuwei_new", "shuwei_old", "sias_shuwei", "sicnu", "simc", "sit", "snut_shuwei",
        "south_soft", "stu", "stum", "suda_post", "sues", "sustech", "swjtu_post", "swust",
        "sysu", "tju", "ucas", "uestc_post", "uestc_shuwei", "uic", "umooc", "urp",
        "ustc_post", "vatuu", "whu_post", "whut", "wist", "xatu_shuwei", "xauat_post", "xhtd",
        "xjtu_post", "xju_post", "xmu", "xsyu_shuwei", "xytc", "ygu", "yl", "ysu_post",
        "yzzy", "zf", "zf_1", "zf_cls", "zf_new", "zju_post", "zptc", "ztvtit"
    )

    fun kindOf(importType: String?): Kind {
        val id = importType?.trim()?.lowercase() ?: ""
        if (id == "thu" || id.startsWith("thu_")) return Kind.THU
        if (id.startsWith("zf")) return if (id == "zf_new") Kind.NEW_ZF else Kind.ZF
        if (id.startsWith("jz")) return Kind.JZ
        if (id.startsWith("qz") || id.contains("_qz_")) return Kind.QZ
        if (id == "urp") return Kind.URP
        if (id == "qg" || id.contains("qingguo")) return Kind.QG
        if (id.contains("shuwei") || id == "shu" || id == "shu2024") return Kind.SW
        if (id.contains("kingo")) return Kind.KINGOSOFT
        if (id.contains("eams")) return Kind.EAMS
        return Kind.GENERIC
    }

    /** 导入页下拉框（系统名 + importType）。 */
    val DISPLAY_TYPES: List<Pair<String, String>> = listOf(
        "正方教务" to "zf",
        "新正方" to "zf_new",
        "金智教务" to "jz",
        "强智教务" to "qz",
        "URP教务" to "urp",
        "青果教务" to "qg",
        "树维教务" to "shuwei_new",
        "Kingosoft" to "kingosoft",
        "EAMS" to "eams",
        "清华THU" to "thu",
        "通用(自动识别)" to "generic"
    )
}
