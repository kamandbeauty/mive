"""Geometry for the Orchard Girl 2D rig.

All coordinates are pixels on the 512 x 768 source sheets, origin top-left.
This module is the single source of truth for the extraction script
(``extract_layers.py``), the pose preview and the Dart rig data.
"""

CANVAS_WIDTH = 512
CANVAS_HEIGHT = 768

# --- joints ---------------------------------------------------------------
# Pivots sit on the real joints of the drawing, not on the layer centres.
JOINTS = {
    "root": (196, 726),        # between the boots, ground contact
    "hip": (196, 498),         # waist inside the apron band
    "spine": (196, 398),       # apron knot height
    "neck": (206, 268),        # base of the neck, top of the collar
    "head": (200, 214),        # atlas/axis, just under the jaw
    "shoulderLeft": (150, 286),
    "elbowLeft": (102, 366),
    "wristLeft": (238, 432),
    "handLeft": (252, 442),
    "shoulderRight": (262, 300),
    "elbowRight": (284, 404),
    "wristRight": (306, 472),
    "handRight": (314, 492),
    "basket": (150, 348),      # where the handle meets the forearm
    "elbowRightSplit": (288, 402),
    "apron": (196, 398),
    "lockLeft": (108, 180),    # upper end of the left side curl
    "lockRight": (276, 178),
    "bangs": (200, 104),
}

# --- face -----------------------------------------------------------------
EYE_LEFT = (154, 150, 212, 204)
EYE_RIGHT = (218, 148, 278, 202)
BROW_LEFT = (148, 118, 218, 152)
BROW_RIGHT = (214, 116, 284, 150)
MOUTH = (180, 204, 254, 254)
LID_LEFT = (154, 150, 212, 204)
LID_RIGHT = (218, 148, 278, 202)
LID_TRAVEL = 52            # px the lid art travels to cover the socket
LID_BOXES = {"left": LID_LEFT, "right": LID_RIGHT}

NECK = [(184, 236), (246, 236), (250, 292), (180, 292)]

# --- head zones -----------------------------------------------------------
HEAD_ZONE = [(20, 24), (340, 24), (344, 252), (316, 318), (196, 326), (66, 312), (20, 248)]

FACE_ZONE = [
    (196, 92), (232, 96), (262, 110), (286, 134), (298, 164),
    (298, 196), (288, 226), (272, 250), (248, 270), (218, 284),
    (194, 284), (166, 276), (142, 258), (126, 234), (116, 202),
    (116, 170), (126, 140), (146, 116), (166, 100), (184, 94),
]

FRONT_HAIR = [
    (110, 178), (118, 134), (140, 100), (176, 82), (212, 78), (252, 88),
    (282, 106), (300, 136), (308, 168), (298, 186), (276, 160), (250, 140),
    (220, 128), (188, 122), (158, 132), (134, 154), (118, 182),
]

LOCK_LEFT = [
    (150, 128), (152, 168), (140, 208), (120, 244), (94, 272),
    (68, 288), (48, 292), (40, 276), (52, 254), (70, 232), (88, 206),
    (100, 178), (108, 148), (120, 130),
]

LOCK_RIGHT = [
    (250, 124), (272, 134), (292, 158), (304, 190), (304, 224),
    (296, 254), (280, 278), (262, 290), (248, 284), (250, 262), (260, 236),
    (266, 206), (264, 176), (254, 150),
]

# --- body -----------------------------------------------------------------
TORSO = [(54, 250), (334, 250), (340, 418), (50, 418)]

APRON_TAIL = [
    (86, 372), (176, 366), (196, 372), (216, 366), (306, 372),
    (316, 392), (326, 412), (330, 448), (330, 500), (324, 528),
    (150, 532), (72, 528), (68, 500), (68, 448), (74, 412), (80, 392),
]

APRON_KNOT = (196, 398)
WAIST_Y = 396
TORSO_BOTTOM_Y = 412
LEGS_TOP_Y = 454
LEGS_BOTTOM_Y = 744

LEGS = [(84, 452), (310, 452), (304, 746), (96, 746)]

# --- arms -----------------------------------------------------------------
# Left arm (holds the basket): sleeve, forearm and hand move as ONE rigid
# piece, so a single closed silhouette is enough. The invisible side runs
# inside the torso (which is drawn behind and rebuilt there).
ARM_LEFT = [
    (152, 252), (176, 258), (198, 264), (212, 272), (219, 284),
    (221, 300), (221, 316), (218, 332), (214, 344), (206, 354),
    (196, 362), (184, 368), (176, 374), (188, 382), (202, 390),
    (216, 398), (228, 406), (238, 414), (244, 422), (242, 430),
    (234, 436), (222, 438), (208, 436), (194, 432), (180, 426),
    (166, 419), (152, 411), (138, 402), (124, 391), (112, 379),
    (100, 366), (90, 352), (84, 338), (82, 322), (84, 306),
    (90, 290), (100, 276), (114, 264), (130, 256),
]

# Right arm: fully visible, split at the elbow. ``ARM_RIGHT`` is the union
# used to punch the sleeve/forearm out of the torso; the two halves are cut
# with a circular cap centred on the elbow.
ARM_RIGHT = [
    (246, 280), (266, 286), (286, 296), (302, 312), (312, 330),
    (314, 344), (308, 352), (298, 358), (288, 366), (292, 382),
    (298, 400), (304, 418), (310, 436), (318, 452), (326, 466),
    (330, 480), (326, 494), (314, 502), (300, 502), (288, 494),
    (282, 480), (276, 462), (270, 444), (264, 424), (258, 402),
    (252, 378), (248, 354), (244, 330), (240, 304),
]

RIGHT_ELBOW = (288, 402)
RIGHT_WRIST = (322, 486)

# --- basket ---------------------------------------------------------------
BASKET_BOWL = [
    (28, 442), (32, 424), (44, 412), (62, 404), (84, 400), (108, 400),
    (132, 406), (154, 416), (172, 430), (182, 448), (184, 468), (176, 490),
    (160, 506), (136, 516), (108, 518), (82, 512), (58, 500), (42, 482),
    (32, 462),
]

BASKET_HANDLE_POLY = [
    (44, 456), (42, 428), (48, 402), (60, 378), (78, 358), (100, 342),
    (122, 332), (144, 328), (160, 334), (166, 346), (160, 358), (146, 362),
    (130, 368), (112, 380), (96, 396), (84, 416), (76, 438), (72, 458),
]

# --- drawing order --------------------------------------------------------
# Back to front. Lids are drawn by the runtime above the eyes, never in the
# static composite, so they are intentionally absent here.
LAYER_ORDER = [
    "hair_back",
    "legs",
    "torso",
    "apron_tail",
    "arm_left",
    "basket_bowl",
    "basket_handle",
    "arm_right_up",
    "arm_right_fore",
    "face_base",
    "eyes",
    "brows",
    "mouth",
    "lock_left",
    "lock_right",
    "hair_front",
]

# --- face variants --------------------------------------------------------
FACE_VARIANTS = {
    "eyes": {
        "open": "idle",
        "wide": "excited",
        "soft": "encourage",
        "closed": "blink",
    },
    "brows": {
        "neutral": "idle",
        "raised": "excited",
        "soft": "encourage",
        "sad": "sad",
        "joy": "happy",
    },
    "mouth": {
        "smile": "idle",
        "grin": "happy",
        "open": "excited",
        "soft": "encourage",
        "sad": "sad",
        "laugh": "celebrate",
    },
}

MOOD_FACE = {
    "idle": {"eyes": "open", "brows": "neutral", "mouth": "smile"},
    "happy": {"eyes": "open", "brows": "joy", "mouth": "grin"},
    "excited": {"eyes": "wide", "brows": "raised", "mouth": "open"},
    "encourage": {"eyes": "soft", "brows": "soft", "mouth": "soft"},
    "sad": {"eyes": "soft", "brows": "sad", "mouth": "sad"},
    "celebrate": {"eyes": "open", "brows": "joy", "mouth": "laugh"},
    "harvest": {"eyes": "open", "brows": "joy", "mouth": "grin"},
}

# Mood poses in degrees, driven by the runtime animation curves.
# Visually safe envelope for the extracted, single-pose limbs: the sleeve has
# no shoulder cap, so a bigger swing tears the shoulder open. The basket hand
# is frozen because the basket is rigidly parented to that arm.
POSE_LIMITS = {
    "head": 9,
    "neck": 6,
    "spine": 4,
    "shoulder": 24,
    "elbow": 20,
    "lock": 12,
}
