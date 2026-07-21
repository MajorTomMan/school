#!/usr/bin/env python3
"""Canonical School course package model.

A published course contains business data only. Download URLs, byte sizes and digests belong to
manifest.json, not course.json. The APK remains the final authority for package validation; these
checks make authoring and CI fail earlier with the same constraints.
"""

from __future__ import annotations

from pathlib import PurePosixPath
from typing import Any

TEXT_STYLES = {"textbook", "explanation", "history", "prompt", "caption"}
SCENE_FIELDS: dict[str, set[str]] = {
    "opposite_quantities": {"title", "scene", "scenes"},
    "rational_classification": {"title", "mode"},
    "integer_to_fraction": {"title"},
    "number_line": {"title", "mode", "signed", "initial"},
    "opposite_numbers": {"title"},
    "absolute_value": {"title"},
    "number_comparison": {"title"},
    "addition_process": {"title"},
    "subtraction_transform": {"title", "expression"},
    "multiplication_sign": {"title"},
    "division_transform": {"title", "expression"},
    "power_process": {"title"},
    "algebra_process": {"title", "left", "right", "note"},
    "equation_balance": {"title", "left", "right", "note"},
    "root_number_line": {"title", "note"},
    "cartesian_plane": {"title", "note"},
    "function_graph": {"title", "function", "note"},
    "geometry": {"title", "shape", "note"},
    "transformation": {"title", "mode", "note"},
    "right_triangle": {"title", "formula", "note"},
    "data_chart": {"title", "mode", "note"},
    "probability": {"title", "note"},
    "projection": {"title", "note"},
    "diagram": {"height", "elements"},
}


def require_keys(value: dict[str, Any], allowed: set[str], location: str) -> None:
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise ValueError(f"{location} contains unsupported fields: {', '.join(unknown)}")


def require_shape(
    value: dict[str, Any],
    required: set[str],
    optional: set[str],
    location: str,
) -> None:
    require_keys(value, required | optional, location)
    missing = sorted(required - set(value))
    if missing:
        raise ValueError(f"{location} is missing required fields: {', '.join(missing)}")


def require_text(value: Any, location: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{location} must be non-empty text")
    return value.strip()


def optional_text_list(value: Any, location: str) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
        raise ValueError(f"{location} must be an array of non-empty text")
    result = [item.strip() for item in value]
    if len(result) != len(set(result)):
        raise ValueError(f"{location} contains duplicate text")
    return result


def relative_path(value: Any, location: str) -> str:
    text = require_text(value, location).replace("\\", "/")
    path = PurePosixPath(text)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError(f"{location} is unsafe: {text}")
    return path.as_posix()


def validate_course(course: dict[str, Any]) -> None:
    require_shape(course, {"textbook", "chapters"}, set(), "course")
    textbook = course.get("textbook")
    if not isinstance(textbook, dict):
        raise ValueError("course.textbook must be an object")
    require_shape(textbook, {"id", "title", "publisher", "edition", "grade", "semester", "subject", "pdf"}, set(), "textbook")
    for key in ("id", "title", "publisher", "edition", "grade", "semester", "subject"):
        require_text(textbook.get(key), f"textbook.{key}")
    pdf = textbook.get("pdf")
    if not isinstance(pdf, dict):
        raise ValueError("textbook.pdf must be an object")
    require_shape(pdf, {"path", "pageCount", "pageIndexOffset"}, set(), "textbook.pdf")
    path = relative_path(pdf.get("path"), "textbook.pdf.path")
    if not path.lower().endswith(".pdf"):
        raise ValueError("textbook.pdf.path must end with .pdf")
    page_count = pdf.get("pageCount")
    if not isinstance(page_count, int) or isinstance(page_count, bool) or page_count <= 0:
        raise ValueError("textbook.pdf.pageCount must be a positive integer")
    offset = pdf.get("pageIndexOffset", 0)
    if not isinstance(offset, int) or isinstance(offset, bool) or not -10_000 <= offset <= 10_000:
        raise ValueError("textbook.pdf.pageIndexOffset is invalid")

    chapters = course.get("chapters")
    if not isinstance(chapters, list) or not chapters:
        raise ValueError("course.chapters must be a non-empty array")
    seen: set[str] = set()
    for chapter_index, chapter in enumerate(chapters):
        validate_chapter(chapter, page_count, seen, f"chapters[{chapter_index}]")


def validate_chapter(chapter: Any, page_count: int, seen: set[str], location: str) -> None:
    if not isinstance(chapter, dict):
        raise ValueError(f"{location} must be an object")
    require_shape(chapter, {"id", "number", "title", "aliases", "sections"}, {"review"}, location)
    validate_unique_id(chapter.get("id"), seen, f"{location}.id")
    require_text(chapter.get("title"), f"{location}.title")
    optional_text_list(chapter.get("aliases"), f"{location}.aliases")
    sections = chapter.get("sections")
    if not isinstance(sections, list) or not sections:
        raise ValueError(f"{location}.sections must be non-empty")
    for index, section in enumerate(sections):
        validate_section(section, page_count, seen, f"{location}.sections[{index}]")
    if chapter.get("review") is not None:
        validate_section(chapter["review"], page_count, seen, f"{location}.review")


def validate_section(section: Any, page_count: int, seen: set[str], location: str) -> None:
    if not isinstance(section, dict):
        raise ValueError(f"{location} must be an object")
    require_shape(section, {"id", "title", "aliases", "pages"}, {"number"}, location)
    validate_unique_id(section.get("id"), seen, f"{location}.id")
    require_text(section.get("title"), f"{location}.title")
    optional_text_list(section.get("aliases"), f"{location}.aliases")
    pages = section.get("pages")
    if not isinstance(pages, list) or not pages:
        raise ValueError(f"{location}.pages must be non-empty")
    for index, page in enumerate(pages):
        validate_page(page, page_count, seen, f"{location}.pages[{index}]")


def validate_page(page: Any, page_count: int, seen: set[str], location: str) -> None:
    if not isinstance(page, dict):
        raise ValueError(f"{location} must be an object")
    require_shape(page, {"id", "title", "sourcePage", "blocks"}, {"aliases", "sourcePageEnd"}, location)
    validate_unique_id(page.get("id"), seen, f"{location}.id")
    require_text(page.get("title"), f"{location}.title")
    optional_text_list(page.get("aliases"), f"{location}.aliases")
    start = page.get("sourcePage")
    end = page.get("sourcePageEnd", start)
    if not isinstance(start, int) or isinstance(start, bool) or start <= 0:
        raise ValueError(f"{location}.sourcePage must be positive")
    if not isinstance(end, int) or isinstance(end, bool) or not start <= end <= page_count:
        raise ValueError(f"{location}.sourcePageEnd is invalid")
    blocks = page.get("blocks")
    if not isinstance(blocks, list) or not blocks:
        raise ValueError(f"{location}.blocks must be non-empty")
    for index, block in enumerate(blocks):
        validate_block(block, f"{location}.blocks[{index}]")


def validate_block(block: Any, location: str) -> None:
    if not isinstance(block, dict):
        raise ValueError(f"{location} must be an object")
    kind = require_text(block.get("type"), f"{location}.type")
    if kind == "heading":
        require_shape(block, {"type", "text"}, set(), location)
        require_text(block.get("text"), f"{location}.text")
    elif kind == "text":
        require_shape(block, {"type", "style", "text"}, set(), location)
        if block.get("style") not in TEXT_STYLES:
            raise ValueError(f"{location}.style is unsupported")
        require_text(block.get("text"), f"{location}.text")
    elif kind == "formula":
        require_shape(block, {"type", "expression"}, {"conditions"}, location)
        require_text(block.get("expression"), f"{location}.expression")
        optional_text_list(block.get("conditions"), f"{location}.conditions")
    elif kind == "list":
        require_shape(block, {"type", "items"}, set(), location)
        if not optional_text_list(block.get("items"), f"{location}.items"):
            raise ValueError(f"{location}.items must be non-empty")
    elif kind == "example":
        require_shape(block, {"type", "statement"}, {"label", "steps", "result"}, location)
        if block.get("label") is not None:
            require_text(block.get("label"), f"{location}.label")
        require_text(block.get("statement"), f"{location}.statement")
        optional_text_list(block.get("steps"), f"{location}.steps")
        if block.get("result") is not None:
            require_text(block.get("result"), f"{location}.result")
    elif kind == "exercise":
        require_shape(block, {"type", "stem"}, {"number", "choices", "hints"}, location)
        if block.get("number") is not None and not isinstance(block.get("number"), str):
            raise ValueError(f"{location}.number must be text")
        require_text(block.get("stem"), f"{location}.stem")
        optional_text_list(block.get("choices"), f"{location}.choices")
        optional_text_list(block.get("hints"), f"{location}.hints")
    elif kind == "conclusion":
        require_shape(block, {"type", "text"}, set(), location)
        require_text(block.get("text"), f"{location}.text")
    elif kind == "scene":
        require_shape(block, {"type", "template", "data"}, set(), location)
        template = require_text(block.get("template"), f"{location}.template")
        allowed = SCENE_FIELDS.get(template)
        if allowed is None:
            raise ValueError(f"{location}.template is unsupported: {template}")
        data = block.get("data", {})
        if not isinstance(data, dict):
            raise ValueError(f"{location}.data must be an object")
        require_keys(data, allowed, f"{location}.data")
        validate_scene(template, data, location)
    else:
        raise ValueError(f"{location}.type is unsupported: {kind}")


def validate_scene(template: str, data: dict[str, Any], location: str) -> None:
    for key in ("title", "left", "right", "note", "expression", "formula"):
        if key in data:
            require_text(data[key], f"{location}.data.{key}")
    if template == "opposite_quantities":
        scenes = data.get("scenes")
        if scenes is not None:
            values = optional_text_list(scenes, f"{location}.data.scenes")
            allowed = {"temperature", "account", "elevation", "change", "tolerance", "deviation"}
            if not values or any(value not in allowed for value in values):
                raise ValueError(f"{location}.data.scenes contains unsupported values")
        if data.get("scene") is not None and data["scene"] not in {"temperature", "account", "elevation", "change", "tolerance", "deviation"}:
            raise ValueError(f"{location}.data.scene is unsupported")
    elif template == "rational_classification" and data.get("mode") not in {None, "definition", "fraction_form"}:
        raise ValueError(f"{location}.data.mode is unsupported")
    elif template == "number_line":
        if data.get("mode") not in {None, "road", "construction", "value", "example", "read_points"}:
            raise ValueError(f"{location}.data.mode is unsupported")
        if "signed" in data and not isinstance(data["signed"], bool):
            raise ValueError(f"{location}.data.signed must be boolean")
        if "initial" in data and (not isinstance(data["initial"], (int, float)) or isinstance(data["initial"], bool)):
            raise ValueError(f"{location}.data.initial must be numeric")
    elif template == "function_graph" and data.get("function") not in {None, "linear", "quadratic", "inverse"}:
        raise ValueError(f"{location}.data.function is unsupported")
    elif template == "geometry" and data.get("shape") not in {None, "triangle", "parallel", "circle"}:
        raise ValueError(f"{location}.data.shape is unsupported")
    elif template == "transformation" and data.get("mode") not in {None, "translation", "rotation", "symmetry"}:
        raise ValueError(f"{location}.data.mode is unsupported")
    elif template == "data_chart" and data.get("mode") not in {None, "bar", "line"}:
        raise ValueError(f"{location}.data.mode is unsupported")
    elif template == "diagram":
        validate_diagram(data, location)


def validate_diagram(data: dict[str, Any], location: str) -> None:
    height = data.get("height", 320)
    require_number(height, f"{location}.data.height", 120, 1000)
    elements = data.get("elements")
    if not isinstance(elements, list) or not elements:
        raise ValueError(f"{location}.data.elements must be non-empty")
    for index, element in enumerate(elements):
        validate_diagram_element(element, f"{location}.data.elements[{index}]")


def validate_diagram_element(element: Any, location: str) -> None:
    if not isinstance(element, dict):
        raise ValueError(f"{location} must be an object")
    kind = require_text(element.get("type"), f"{location}.type")
    common = {"type", "color", "stroke"}
    fields = {
        "line": {"x1", "y1", "x2", "y2"},
        "arrow": {"x1", "y1", "x2", "y2"},
        "point": {"x", "y", "radius"},
        "circle": {"x", "y", "radius"},
        "rectangle": {"x", "y", "width", "height"},
        "text": {"x", "y", "text", "size"},
        "polyline": {"points"},
        "number_line": {"x1", "x2", "y", "min", "max", "step"},
    }.get(kind)
    if fields is None:
        raise ValueError(f"{location}.type is unsupported: {kind}")
    require_keys(element, common | fields, location)
    if "color" in element and element["color"] not in {"blue", "yellow", "muted", "white"}:
        raise ValueError(f"{location}.color is unsupported")
    if "stroke" in element:
        require_number(element["stroke"], f"{location}.stroke", 0.5, 20)

    def ratio(key: str, required: bool = True) -> None:
        if required or key in element:
            require_number(element.get(key), f"{location}.{key}", 0, 1)

    if kind in {"line", "arrow"}:
        for key in ("x1", "y1", "x2", "y2"):
            ratio(key)
    elif kind in {"point", "circle"}:
        ratio("x")
        ratio("y")
        require_number(element.get("radius"), f"{location}.radius", 0.001, 1)
    elif kind == "rectangle":
        ratio("x")
        ratio("y")
        require_number(element.get("width"), f"{location}.width", 0.001, 1)
        require_number(element.get("height"), f"{location}.height", 0.001, 1)
    elif kind == "text":
        ratio("x")
        ratio("y")
        require_text(element.get("text"), f"{location}.text")
        if "size" in element:
            require_number(element["size"], f"{location}.size", 8, 72)
    elif kind == "polyline":
        points = element.get("points")
        if not isinstance(points, list) or len(points) < 2:
            raise ValueError(f"{location}.points must contain at least two points")
        for index, point in enumerate(points):
            point_location = f"{location}.points[{index}]"
            if not isinstance(point, dict):
                raise ValueError(f"{point_location} must be an object")
            require_keys(point, {"x", "y"}, point_location)
            if set(point) != {"x", "y"}:
                raise ValueError(f"{point_location} must contain x and y")
            require_number(point["x"], f"{point_location}.x", 0, 1)
            require_number(point["y"], f"{point_location}.y", 0, 1)
    elif kind == "number_line":
        ratio("x1", False)
        ratio("x2", False)
        ratio("y", False)
        minimum = element.get("min", -5)
        maximum = element.get("max", 5)
        step = element.get("step", 1)
        require_finite_number(minimum, f"{location}.min")
        require_finite_number(maximum, f"{location}.max")
        require_finite_number(step, f"{location}.step")
        if not minimum < maximum:
            raise ValueError(f"{location} has an invalid number-line range")
        if step <= 0 or (maximum - minimum) / step > 200:
            raise ValueError(f"{location}.step is invalid")


def require_finite_number(value: Any, location: str) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        raise ValueError(f"{location} must be numeric")
    number = float(value)
    if not (-float("inf") < number < float("inf")):
        raise ValueError(f"{location} must be finite")
    return number


def require_number(value: Any, location: str, minimum: float, maximum: float) -> float:
    number = require_finite_number(value, location)
    if not minimum <= number <= maximum:
        raise ValueError(f"{location} must be between {minimum} and {maximum}")
    return number


def validate_unique_id(value: Any, seen: set[str], location: str) -> None:
    identifier = require_text(value, location)
    if any(not (character.isalnum() or character in "._-") for character in identifier):
        raise ValueError(f"{location} has an invalid identifier")
    if identifier in seen:
        raise ValueError(f"duplicate id: {identifier}")
    seen.add(identifier)
