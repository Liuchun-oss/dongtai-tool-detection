#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
将Word文档转换为专业的PPT演示文稿（增强版）
"""

import os
import re
from docx import Document
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.dml.color import RGBColor

def read_word_document(docx_file):
    """读取Word文档内容，返回结构化数据"""
    try:
        doc = Document(docx_file)
        sections = []
        current_section = None
        
        for para in doc.paragraphs:
            text = para.text.strip()
            if not text:
                continue
            
            style_name = para.style.name if para.style else ''
            
            # 判断标题级别
            if 'Heading 1' in style_name or para.style.name == 'Title':
                if current_section:
                    sections.append(current_section)
                current_section = {
                    'title': text,
                    'level': 1,
                    'subsections': [],
                    'content': []
                }
            elif 'Heading 2' in style_name:
                if current_section:
                    current_section['subsections'].append({
                        'title': text,
                        'level': 2,
                        'content': []
                    })
                else:
                    current_section = {
                        'title': text,
                        'level': 2,
                        'subsections': [],
                        'content': []
                    }
            elif 'Heading 3' in style_name:
                if current_section and current_section['subsections']:
                    current_section['subsections'][-1]['content'].append({
                        'type': 'subheading',
                        'text': text,
                        'level': 3
                    })
                elif current_section:
                    current_section['content'].append({
                        'type': 'subheading',
                        'text': text,
                        'level': 3
                    })
            else:
                # 普通段落
                if current_section and current_section['subsections']:
                    current_section['subsections'][-1]['content'].append({
                        'type': 'paragraph',
                        'text': text
                    })
                elif current_section:
                    current_section['content'].append({
                        'type': 'paragraph',
                        'text': text
                    })
        
        # 处理表格
        for table in doc.tables:
            table_data = []
            for row in table.rows:
                row_data = [cell.text.strip() for cell in row.cells]
                if any(row_data):  # 跳过空行
                    table_data.append(row_data)
            
            if table_data:
                if current_section and current_section['subsections']:
                    current_section['subsections'][-1]['content'].append({
                        'type': 'table',
                        'data': table_data
                    })
                elif current_section:
                    current_section['content'].append({
                        'type': 'table',
                        'data': table_data
                    })
        
        if current_section:
            sections.append(current_section)
        
        return sections
    except Exception as e:
        print(f"读取Word文档时出错: {e}")
        import traceback
        traceback.print_exc()
        return None

def create_title_slide(prs, title, subtitle="动态车间工具智能清点系统"):
    """创建标题幻灯片"""
    slide = prs.slides.add_slide(prs.slide_layouts[0])
    
    # 设置标题
    title_shape = slide.shapes.title
    title_shape.text = title
    for paragraph in title_shape.text_frame.paragraphs:
        paragraph.alignment = PP_ALIGN.CENTER
        for run in paragraph.runs:
            run.font.name = '微软雅黑'
            run.font.size = Pt(44)
            run.font.bold = True
            run.font.color.rgb = RGBColor(0, 51, 102)
    
    # 设置副标题
    if len(slide.placeholders) > 1:
        subtitle_shape = slide.placeholders[1]
        subtitle_shape.text = subtitle
        for paragraph in subtitle_shape.text_frame.paragraphs:
            paragraph.alignment = PP_ALIGN.CENTER
            for run in paragraph.runs:
                run.font.name = '微软雅黑'
                run.font.size = Pt(24)
                run.font.color.rgb = RGBColor(64, 64, 64)
    
    return slide

def create_content_slide(prs, title, content_items):
    """创建内容幻灯片"""
    slide = prs.slides.add_slide(prs.slide_layouts[1])
    
    # 设置标题
    title_shape = slide.shapes.title
    title_shape.text = title
    for paragraph in title_shape.text_frame.paragraphs:
        for run in paragraph.runs:
            run.font.name = '微软雅黑'
            run.font.size = Pt(36)
            run.font.bold = True
            run.font.color.rgb = RGBColor(0, 51, 102)
    
    # 添加内容
    if len(slide.placeholders) > 1:
        content_placeholder = slide.placeholders[1].text_frame
    else:
        # 如果没有占位符，创建文本框
        left = Inches(0.5)
        top = Inches(1.8)
        width = Inches(9)
        height = Inches(5.2)
        text_box = slide.shapes.add_textbox(left, top, width, height)
        content_placeholder = text_box.text_frame
    
    content_placeholder.word_wrap = True
    content_placeholder.clear()
    
    for item in content_items:
        if item['type'] == 'subheading':
            p = content_placeholder.add_paragraph()
            p.text = item['text']
            p.level = 0
            p.space_before = Pt(6)
            p.space_after = Pt(4)
            
            for run in p.runs:
                run.font.name = '微软雅黑'
                run.font.size = Pt(20)
                run.font.bold = True
                run.font.color.rgb = RGBColor(0, 102, 204)
        
        elif item['type'] == 'paragraph':
            text = item['text']
            # 处理列表项
            if text.startswith('- ') or text.startswith('* ') or text.startswith('• '):
                p = content_placeholder.add_paragraph()
                p.text = text[2:].strip()
                p.level = 0
                p.space_after = Pt(3)
                
                for run in p.runs:
                    run.font.name = '微软雅黑'
                    run.font.size = Pt(18)
            elif text and len(text) > 2 and text[0].isdigit() and text[1] == '.':
                p = content_placeholder.add_paragraph()
                p.text = text.split('. ', 1)[1] if '. ' in text else text
                p.level = 0
                p.space_after = Pt(3)
                
                for run in p.runs:
                    run.font.name = '微软雅黑'
                    run.font.size = Pt(18)
            else:
                # 普通段落，限制长度
                if len(text) > 200:
                    # 长段落分段
                    sentences = re.split(r'[。！？]', text)
                    current_text = ""
                    for sentence in sentences:
                        if sentence.strip():
                            if len(current_text + sentence) > 200:
                                if current_text:
                                    p = content_placeholder.add_paragraph()
                                    p.text = current_text.strip()
                                    p.space_after = Pt(4)
                                    for run in p.runs:
                                        run.font.name = '微软雅黑'
                                        run.font.size = Pt(18)
                                current_text = sentence + "。"
                            else:
                                current_text += sentence + "。"
                    if current_text:
                        p = content_placeholder.add_paragraph()
                        p.text = current_text.strip()
                        p.space_after = Pt(4)
                        for run in p.runs:
                            run.font.name = '微软雅黑'
                            run.font.size = Pt(18)
                else:
                    p = content_placeholder.add_paragraph()
                    p.text = text
                    p.space_after = Pt(6)
                    
                    for run in p.runs:
                        run.font.name = '微软雅黑'
                        run.font.size = Pt(18)
        
        elif item['type'] == 'table':
            # 表格单独创建幻灯片
            return slide, item['data']
    
    return slide, None

def create_table_slide(prs, title, table_data):
    """创建表格幻灯片"""
    slide = prs.slides.add_slide(prs.slide_layouts[5])  # Blank
    
    # 添加标题
    left = Inches(0.5)
    top = Inches(0.3)
    width = Inches(9)
    height = Inches(0.8)
    title_box = slide.shapes.add_textbox(left, top, width, height)
    title_frame = title_box.text_frame
    title_frame.text = title
    title_frame.word_wrap = True
    
    for paragraph in title_frame.paragraphs:
        paragraph.alignment = PP_ALIGN.LEFT
        for run in paragraph.runs:
            run.font.name = '微软雅黑'
            run.font.size = Pt(28)
            run.font.bold = True
            run.font.color.rgb = RGBColor(0, 51, 102)
    
    # 创建表格
    if not table_data or len(table_data) < 2:
        return slide
    
    rows = len(table_data)
    cols = len(table_data[0])
    
    table_left = Inches(0.5)
    table_top = Inches(1.3)
    table_width = Inches(9)
    table_height = min(Inches(5.5), Inches(0.5 * rows))
    
    table = slide.shapes.add_table(rows, cols, table_left, table_top, table_width, table_height).table
    
    # 填充数据
    for i, row_data in enumerate(table_data):
        for j, cell_data in enumerate(row_data):
            if j < cols:
                cell = table.cell(i, j)
                cell.text = cell_data
                
                # 设置字体和格式
                for paragraph in cell.text_frame.paragraphs:
                    paragraph.alignment = PP_ALIGN.CENTER
                    paragraph.space_before = Pt(2)
                    paragraph.space_after = Pt(2)
                    for run in paragraph.runs:
                        run.font.name = '微软雅黑'
                        if i == 0:  # 表头
                            run.font.size = Pt(14)
                            run.font.bold = True
                            run.font.color.rgb = RGBColor(255, 255, 255)
                        else:
                            run.font.size = Pt(12)
                            run.font.color.rgb = RGBColor(0, 0, 0)
                
                # 设置单元格边距
                cell.margin_left = Inches(0.05)
                cell.margin_right = Inches(0.05)
                cell.margin_top = Inches(0.03)
                cell.margin_bottom = Inches(0.03)
                
                # 设置表头背景色
                if i == 0:
                    cell.fill.solid()
                    cell.fill.fore_color.rgb = RGBColor(0, 51, 102)
    
    return slide

def create_ppt_from_word(docx_file, pptx_file):
    """从Word文档创建PPT"""
    
    print(f"正在读取Word文档: {docx_file}")
    sections = read_word_document(docx_file)
    
    if not sections:
        print("无法读取Word文档内容")
        return False
    
    # 创建PPT
    prs = Presentation()
    prs.slide_width = Inches(10)
    prs.slide_height = Inches(7.5)
    
    # 创建标题幻灯片
    create_title_slide(prs, "动态车间工具智能清点系统", "项目汇报")
    
    print(f"正在创建PPT，共 {len(sections)} 个主要章节...")
    
    # 处理每个章节
    for section in sections:
        # 创建章节标题幻灯片
        create_title_slide(prs, section['title'])
        
        # 处理子章节
        if section['subsections']:
            for subsection in section['subsections']:
                slide, table_data = create_content_slide(prs, subsection['title'], subsection['content'])
                
                # 如果有表格，创建单独的表格幻灯片
                if table_data:
                    create_table_slide(prs, subsection['title'], table_data)
        else:
            # 处理主章节内容
            # 检查是否有表格
            has_table = any(item.get('type') == 'table' for item in section['content'])
            
            if has_table:
                # 分离表格和其他内容
                content_items = []
                table_items = []
                
                for item in section['content']:
                    if item['type'] == 'table':
                        table_items.append(item['data'])
                    else:
                        content_items.append(item)
                
                # 创建内容幻灯片
                if content_items:
                    create_content_slide(prs, section['title'], content_items)
                
                # 创建表格幻灯片
                for table_data in table_items:
                    create_table_slide(prs, section['title'], table_data)
            else:
                create_content_slide(prs, section['title'], section['content'])
    
    # 保存PPT
    prs.save(pptx_file)
    print(f"PPT已成功创建: {pptx_file}")
    print(f"共创建 {len(prs.slides)} 张幻灯片")
    return True

if __name__ == '__main__':
    docx_file = '项目汇报文档new.docx'
    pptx_file = '项目汇报演示文稿.pptx'
    
    if not os.path.exists(docx_file):
        print(f"错误: 找不到文件 {docx_file}")
        print("请确保文件存在")
    else:
        try:
            create_ppt_from_word(docx_file, pptx_file)
        except ImportError:
            print("错误: 需要安装 python-pptx 库")
            print("请运行: pip install python-pptx")
        except Exception as e:
            print(f"创建PPT时出错: {e}")
            import traceback
            traceback.print_exc()
