#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
将Word文档转换为PPT演示文稿
"""

import os
from docx import Document
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.dml.color import RGBColor

def set_chinese_font(run, font_name='微软雅黑'):
    """设置中文字体"""
    run.font.name = font_name
    run._element.rPr.rFonts.set('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}ascii', font_name)
    run._element.rPr.rFonts.set('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}eastAsia', font_name)

def read_word_document(docx_file):
    """读取Word文档内容"""
    try:
        doc = Document(docx_file)
        content = []
        
        for para in doc.paragraphs:
            if para.text.strip():
                # 判断段落类型
                style_name = para.style.name if para.style else ''
                
                # 判断是否为标题
                if 'Heading' in style_name or para.style.name.startswith('Heading'):
                    level = 1
                    if 'Heading 1' in style_name:
                        level = 1
                    elif 'Heading 2' in style_name:
                        level = 2
                    elif 'Heading 3' in style_name:
                        level = 3
                    elif 'Heading 4' in style_name:
                        level = 4
                    else:
                        level = 1
                    
                    content.append({
                        'type': 'heading',
                        'level': level,
                        'text': para.text.strip()
                    })
                else:
                    content.append({
                        'type': 'paragraph',
                        'text': para.text.strip()
                    })
        
        # 处理表格
        for table in doc.tables:
            table_data = []
            for row in table.rows:
                row_data = [cell.text.strip() for cell in row.cells]
                table_data.append(row_data)
            if table_data:
                content.append({
                    'type': 'table',
                    'data': table_data
                })
        
        return content
    except Exception as e:
        print(f"读取Word文档时出错: {e}")
        import traceback
        traceback.print_exc()
        return None

def create_ppt_from_word(docx_file, pptx_file):
    """从Word文档创建PPT"""
    
    # 读取Word文档
    print(f"正在读取Word文档: {docx_file}")
    content = read_word_document(docx_file)
    
    if not content:
        print("无法读取Word文档内容")
        return False
    
    # 创建PPT
    prs = Presentation()
    prs.slide_width = Inches(10)
    prs.slide_height = Inches(7.5)
    
    current_slide = None
    current_content = []
    
    print(f"正在创建PPT，共 {len(content)} 个内容块...")
    
    for i, item in enumerate(content):
        if item['type'] == 'heading':
            # 如果是标题，创建新幻灯片
            if current_slide and current_content:
                # 先处理之前累积的内容
                add_content_to_slide(current_slide, current_content)
                current_content = []
            
            # 根据标题级别创建不同类型的幻灯片
            if item['level'] == 1:
                # 一级标题 - 标题幻灯片
                slide = prs.slides.add_slide(prs.slide_layouts[0])  # Title Slide
                title = slide.shapes.title
                subtitle = slide.placeholders[1]
                
                title.text = item['text']
                subtitle.text = "动态车间工具智能清点系统"
                
                # 设置字体
                for paragraph in title.text_frame.paragraphs:
                    for run in paragraph.runs:
                        run.font.name = '微软雅黑'
                        run.font.size = Pt(44)
                        run.font.bold = True
                
                for paragraph in subtitle.text_frame.paragraphs:
                    for run in paragraph.runs:
                        run.font.name = '微软雅黑'
                        run.font.size = Pt(24)
                
                current_slide = slide
            elif item['level'] == 2:
                # 二级标题 - 标题和内容幻灯片
                slide = prs.slides.add_slide(prs.slide_layouts[1])  # Title and Content
                title = slide.shapes.title
                title.text = item['text']
                
                for paragraph in title.text_frame.paragraphs:
                    for run in paragraph.runs:
                        run.font.name = '微软雅黑'
                        run.font.size = Pt(36)
                        run.font.bold = True
                
                current_slide = slide
                current_content = []
            else:
                # 三级及以下标题 - 作为内容添加到当前幻灯片
                if not current_slide:
                    slide = prs.slides.add_slide(prs.slide_layouts[1])
                    title = slide.shapes.title
                    title.text = "内容"
                    current_slide = slide
                    current_content = []
                
                current_content.append({
                    'type': 'subheading',
                    'level': item['level'],
                    'text': item['text']
                })
        
        elif item['type'] == 'paragraph':
            # 段落内容
            if not current_slide:
                slide = prs.slides.add_slide(prs.slide_layouts[1])
                title = slide.shapes.title
                title.text = "内容"
                current_slide = slide
                current_content = []
            
            current_content.append(item)
        
        elif item['type'] == 'table':
            # 表格 - 创建新幻灯片
            if current_slide and current_content:
                add_content_to_slide(current_slide, current_content)
                current_content = []
            
            slide = prs.slides.add_slide(prs.slide_layouts[5])  # Blank
            add_table_to_slide(slide, item['data'])
            current_slide = None
            current_content = []
    
    # 处理最后累积的内容
    if current_slide and current_content:
        add_content_to_slide(current_slide, current_content)
    
    # 保存PPT
    prs.save(pptx_file)
    print(f"PPT已成功创建: {pptx_file}")
    print(f"共创建 {len(prs.slides)} 张幻灯片")
    return True

def add_content_to_slide(slide, content_items):
    """将内容添加到幻灯片"""
    if not content_items:
        return
    
    # 获取内容占位符
    try:
        content_placeholder = slide.placeholders[1]
    except:
        # 如果没有占位符，创建文本框
        left = Inches(0.5)
        top = Inches(1.5)
        width = Inches(9)
        height = Inches(5.5)
        text_box = slide.shapes.add_textbox(left, top, width, height)
        content_placeholder = text_box.text_frame
    else:
        content_placeholder = slide.placeholders[1].text_frame
    
    content_placeholder.word_wrap = True
    content_placeholder.auto_size = MSO_ANCHOR.TOP
    
    # 清空现有内容
    content_placeholder.clear()
    
    for item in content_items:
        if item['type'] == 'subheading':
            p = content_placeholder.add_paragraph()
            p.text = item['text']
            p.level = item['level'] - 2  # 调整级别
            p.space_after = Pt(6)
            
            for run in p.runs:
                run.font.name = '微软雅黑'
                run.font.size = Pt(20 - item['level'] * 2)
                run.font.bold = True
        
        elif item['type'] == 'paragraph':
            # 检查是否是列表项
            text = item['text']
            if text.startswith('- ') or text.startswith('* ') or text.startswith('• '):
                p = content_placeholder.add_paragraph()
                p.text = text[2:].strip()
                p.level = 0
                p.space_after = Pt(4)
                
                for run in p.runs:
                    run.font.name = '微软雅黑'
                    run.font.size = Pt(16)
            elif text and text[0].isdigit() and '. ' in text[:5]:
                p = content_placeholder.add_paragraph()
                p.text = text.split('. ', 1)[1] if '. ' in text else text
                p.level = 0
                p.space_after = Pt(4)
                
                for run in p.runs:
                    run.font.name = '微软雅黑'
                    run.font.size = Pt(16)
            else:
                p = content_placeholder.add_paragraph()
                p.text = text
                p.level = 0
                p.space_after = Pt(8)
                
                for run in p.runs:
                    run.font.name = '微软雅黑'
                    run.font.size = Pt(16)

def add_table_to_slide(slide, table_data):
    """在幻灯片中添加表格"""
    if not table_data or len(table_data) < 2:
        return
    
    rows = len(table_data)
    cols = len(table_data[0])
    
    # 计算表格位置和大小
    left = Inches(0.5)
    top = Inches(1.5)
    width = Inches(9)
    height = Inches(5)
    
    # 创建表格
    table = slide.shapes.add_table(rows, cols, left, top, width, height).table
    
    # 填充数据
    for i, row_data in enumerate(table_data):
        for j, cell_data in enumerate(row_data):
            if j < cols:
                cell = table.cell(i, j)
                cell.text = cell_data
                
                # 设置字体
                for paragraph in cell.text_frame.paragraphs:
                    paragraph.alignment = PP_ALIGN.CENTER
                    for run in paragraph.runs:
                        run.font.name = '微软雅黑'
                        if i == 0:  # 表头
                            run.font.size = Pt(14)
                            run.font.bold = True
                        else:
                            run.font.size = Pt(12)
                
                # 设置单元格边距
                cell.margin_left = Inches(0.1)
                cell.margin_right = Inches(0.1)
                cell.margin_top = Inches(0.05)
                cell.margin_bottom = Inches(0.05)

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
