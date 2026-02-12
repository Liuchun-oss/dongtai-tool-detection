#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
将Word文档转换为PPT演示文稿（优化版 - 确保内容不超出屏幕）
"""

import os
import re
from docx import Document
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.dml.color import RGBColor

def read_word_document(docx_file):
    """读取Word文档内容"""
    try:
        doc = Document(docx_file)
        content = []
        
        for para in doc.paragraphs:
            text = para.text.strip()
            if not text:
                continue
            
            style_name = para.style.name if para.style else ''
            
            if 'Heading 1' in style_name:
                content.append({'type': 'heading1', 'text': text})
            elif 'Heading 2' in style_name:
                content.append({'type': 'heading2', 'text': text})
            elif 'Heading 3' in style_name:
                content.append({'type': 'heading3', 'text': text})
            else:
                content.append({'type': 'paragraph', 'text': text})
        
        # 处理表格
        for table in doc.tables:
            table_data = []
            for row in table.rows:
                row_data = [cell.text.strip() for cell in row.cells]
                if any(row_data):
                    table_data.append(row_data)
            if table_data:
                content.append({'type': 'table', 'data': table_data})
        
        return content
    except Exception as e:
        print(f"读取Word文档时出错: {e}")
        import traceback
        traceback.print_exc()
        return None

def create_title_slide(prs):
    """创建标题幻灯片"""
    slide = prs.slides.add_slide(prs.slide_layouts[0])
    
    title_shape = slide.shapes.title
    title_shape.text = "动态车间工具智能清点系统"
    for paragraph in title_shape.text_frame.paragraphs:
        paragraph.alignment = PP_ALIGN.CENTER
        for run in paragraph.runs:
            run.font.name = '微软雅黑'
            run.font.size = Pt(40)
            run.font.bold = True
            run.font.color.rgb = RGBColor(0, 51, 102)
    
    if len(slide.placeholders) > 1:
        subtitle_shape = slide.placeholders[1]
        subtitle_shape.text = "项目汇报"
        for paragraph in subtitle_shape.text_frame.paragraphs:
            paragraph.alignment = PP_ALIGN.CENTER
            for run in paragraph.runs:
                run.font.name = '微软雅黑'
                run.font.size = Pt(20)
                run.font.color.rgb = RGBColor(64, 64, 64)

def add_text_to_slide(slide, text, is_title=False, level=0):
    """向幻灯片添加文本，确保不超出屏幕"""
    if is_title:
        # 标题
        title_shape = slide.shapes.title
        title_shape.text = text
        for paragraph in title_shape.text_frame.paragraphs:
            paragraph.alignment = PP_ALIGN.LEFT
            for run in paragraph.runs:
                run.font.name = '微软雅黑'
                run.font.size = Pt(28)  # 减小标题字体
                run.font.bold = True
                run.font.color.rgb = RGBColor(0, 51, 102)
    else:
        # 内容
        try:
            content_placeholder = slide.placeholders[1].text_frame
        except:
            # 创建文本框，确保在屏幕范围内
            left = Inches(0.5)
            top = Inches(1.6)  # 稍微上移
            width = Inches(9)
            height = Inches(5.5)  # 增加高度限制
            text_box = slide.shapes.add_textbox(left, top, width, height)
            content_placeholder = text_box.text_frame
        
        content_placeholder.word_wrap = True
        content_placeholder.auto_size = MSO_ANCHOR.TOP
        content_placeholder.margin_left = Inches(0.1)
        content_placeholder.margin_right = Inches(0.1)
        content_placeholder.margin_top = Inches(0.1)
        content_placeholder.margin_bottom = Inches(0.1)
        
        # 添加段落
        p = content_placeholder.add_paragraph()
        p.text = text
        p.level = level
        p.space_after = Pt(4)  # 减小间距
        
        # 设置字体大小，根据级别调整
        font_size = Pt(18) if level == 0 else Pt(16)  # 稍微增大字体
        for run in p.runs:
            run.font.name = '微软雅黑'
            run.font.size = font_size

def create_content_slide(prs, title, items, max_items_per_slide=6):
    """创建内容幻灯片，自动分页"""
    slides = []
    current_items = []
    item_count = 0
    
    for item in items:
        if item['type'] == 'heading3':
            # 三级标题
            if current_items and item_count >= max_items_per_slide:
                # 创建当前幻灯片
                slide = prs.slides.add_slide(prs.slide_layouts[1])
                add_text_to_slide(slide, title, is_title=True)
                
                for ci in current_items:
                    if ci['type'] == 'heading3':
                        add_text_to_slide(slide, ci['text'], level=0)
                    elif ci['type'] == 'paragraph':
                        text = ci['text']
                        # 处理列表
                        if text.startswith(('- ', '* ', '• ', '1. ', '2. ', '3. ', '4. ', '5. ')):
                            add_text_to_slide(slide, text, level=0)
                        else:
                            # 长文本分段
                            if len(text) > 150:
                                sentences = re.split(r'[。！？；]', text)
                                current_text = ""
                                for sentence in sentences:
                                    if sentence.strip():
                                        if len(current_text + sentence) > 150:
                                            if current_text:
                                                add_text_to_slide(slide, current_text.strip(), level=0)
                                            current_text = sentence + "。"
                                        else:
                                            current_text += sentence + "。"
                                if current_text:
                                    add_text_to_slide(slide, current_text.strip(), level=0)
                            else:
                                add_text_to_slide(slide, text, level=0)
                
                slides.append(slide)
                current_items = []
                item_count = 0
            
            current_items.append(item)
            item_count += 2  # 标题占更多空间
        elif item['type'] == 'paragraph':
            text = item['text']
            # 估算内容长度
            text_length = len(text)
            estimated_items = max(1, text_length // 100)
            
            if item_count + estimated_items > max_items_per_slide:
                # 创建当前幻灯片
                slide = prs.slides.add_slide(prs.slide_layouts[1])
                add_text_to_slide(slide, title, is_title=True)
                
                for ci in current_items:
                    if ci['type'] == 'heading3':
                        add_text_to_slide(slide, ci['text'], level=0)
                    elif ci['type'] == 'paragraph':
                        add_text_to_slide(slide, ci['text'], level=0)
                
                slides.append(slide)
                current_items = [item]
                item_count = estimated_items
            else:
                current_items.append(item)
                item_count += estimated_items
        
        elif item['type'] == 'table':
            # 表格单独处理
            if current_items:
                slide = prs.slides.add_slide(prs.slide_layouts[1])
                add_text_to_slide(slide, title, is_title=True)
                for ci in current_items:
                    if ci['type'] == 'heading3':
                        add_text_to_slide(slide, ci['text'], level=0)
                    elif ci['type'] == 'paragraph':
                        add_text_to_slide(slide, ci['text'], level=0)
                slides.append(slide)
                current_items = []
            
            # 创建表格幻灯片
            slide = create_table_slide(prs, title, item['data'])
            slides.append(slide)
    
    # 处理剩余内容
    if current_items:
        slide = prs.slides.add_slide(prs.slide_layouts[1])
        add_text_to_slide(slide, title, is_title=True)
        for ci in current_items:
            if ci['type'] == 'heading3':
                add_text_to_slide(slide, ci['text'], level=0)
            elif ci['type'] == 'paragraph':
                add_text_to_slide(slide, ci['text'], level=0)
        slides.append(slide)
    
    return slides

def create_table_slide(prs, title, table_data):
    """创建表格幻灯片，确保表格不超出屏幕"""
    slide = prs.slides.add_slide(prs.slide_layouts[5])  # Blank
    
    # 添加标题
    title_box = slide.shapes.add_textbox(Inches(0.5), Inches(0.3), Inches(9), Inches(0.6))
    title_frame = title_box.text_frame
    title_frame.text = title
    for paragraph in title_frame.paragraphs:
        for run in paragraph.runs:
            run.font.name = '微软雅黑'
            run.font.size = Pt(24)
            run.font.bold = True
            run.font.color.rgb = RGBColor(0, 51, 102)
    
    if not table_data or len(table_data) < 2:
        return slide
    
    rows = len(table_data)
    cols = len(table_data[0])
    
    # 计算表格大小，确保不超出屏幕
    max_rows = min(rows, 7)  # 最多显示7行
    table_height = min(Inches(0.45 * max_rows), Inches(5.2))
    
    table = slide.shapes.add_table(
        max_rows, cols, 
        Inches(0.5), Inches(1.2), 
        Inches(9), table_height
    ).table
    
    # 填充数据（只填充可见行）
    for i in range(min(rows, max_rows)):
        for j in range(min(cols, len(table_data[i]))):
            cell = table.cell(i, j)
            cell.text = table_data[i][j] if j < len(table_data[i]) else ""
            
            for paragraph in cell.text_frame.paragraphs:
                paragraph.alignment = PP_ALIGN.CENTER
                paragraph.space_before = Pt(1)
                paragraph.space_after = Pt(1)
                for run in paragraph.runs:
                    run.font.name = '微软雅黑'
                    if i == 0:
                        run.font.size = Pt(12)
                        run.font.bold = True
                        run.font.color.rgb = RGBColor(255, 255, 255)
                    else:
                        run.font.size = Pt(11)
                        run.font.color.rgb = RGBColor(0, 0, 0)
            
            cell.margin_left = Inches(0.03)
            cell.margin_right = Inches(0.03)
            cell.margin_top = Inches(0.02)
            cell.margin_bottom = Inches(0.02)
            
            if i == 0:
                cell.fill.solid()
                cell.fill.fore_color.rgb = RGBColor(0, 51, 102)
    
    return slide

def create_ppt_from_word(docx_file, pptx_file):
    """从Word文档创建PPT"""
    print(f"正在读取Word文档: {docx_file}")
    content = read_word_document(docx_file)
    
    if not content:
        print("无法读取Word文档内容")
        return False
    
    prs = Presentation()
    prs.slide_width = Inches(10)
    prs.slide_height = Inches(7.5)
    
    # 创建标题幻灯片
    create_title_slide(prs)
    
    print(f"正在创建PPT，共 {len(content)} 个内容块...")
    
    current_section_title = None
    current_section_items = []
    
    for item in content:
        if item['type'] == 'heading1':
            # 一级标题 - 创建章节标题幻灯片
            if current_section_title and current_section_items:
                # 先处理之前章节的内容
                create_content_slide(prs, current_section_title, current_section_items)
                current_section_items = []
            
            # 创建章节标题幻灯片
            slide = prs.slides.add_slide(prs.slide_layouts[0])
            title_shape = slide.shapes.title
            title_shape.text = item['text']
            for paragraph in title_shape.text_frame.paragraphs:
                paragraph.alignment = PP_ALIGN.CENTER
                for run in paragraph.runs:
                    run.font.name = '微软雅黑'
                    run.font.size = Pt(40)
                    run.font.bold = True
                    run.font.color.rgb = RGBColor(0, 51, 102)
            
            current_section_title = item['text']
        
        elif item['type'] == 'heading2':
            # 二级标题 - 开始新小节
            if current_section_title and current_section_items:
                create_content_slide(prs, current_section_title, current_section_items)
                current_section_items = []
            
            current_section_title = item['text']
        
        elif item['type'] in ['heading3', 'paragraph', 'table']:
            # 添加到当前章节
            current_section_items.append(item)
    
    # 处理最后一个章节
    if current_section_title and current_section_items:
        create_content_slide(prs, current_section_title, current_section_items)
    
    prs.save(pptx_file)
    print(f"PPT已成功创建: {pptx_file}")
    print(f"共创建 {len(prs.slides)} 张幻灯片")
    return True

if __name__ == '__main__':
    docx_file = '项目汇报文档new.docx'
    pptx_file = '项目汇报演示文稿.pptx'
    
    if not os.path.exists(docx_file):
        print(f"错误: 找不到文件 {docx_file}")
    else:
        try:
            create_ppt_from_word(docx_file, pptx_file)
        except Exception as e:
            print(f"创建PPT时出错: {e}")
            import traceback
            traceback.print_exc()
