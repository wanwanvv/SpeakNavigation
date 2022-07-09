# -*-coding:utf-8-*-
from flask import Flask
from flask import request
from transformers import BertTokenizer
import torch
import numpy as np
import re
import os

app = Flask(__name__)


# basedir=os.path.abspath(os.path.dirname(__file__))
# app.config['SQLALCHEMY_COMMIT_ON_TEARDOWN']=True
# app.config['SQLALCHEMY_TRACK_MODIFICATIONS']=True

@app.route('/')
def test():
    return '服务器正常运行'


def load_tokenizer():
    return BertTokenizer.from_pretrained('bert-base-uncased')


def _convert_texts_to_tensors(texts, tokenizer,
                              cls_token_segment_id=0,
                              pad_token_segment_id=0,
                              sequence_a_segment_id=0,
                              mask_padding_with_zero=True):
    """
    Only add input_ids, attention_mask, token_type_ids
    Labels aren't required.
    """
    # Setting based on the current model type
    pad_token_label_id = 100
    cls_token = tokenizer.cls_token
    sep_token = tokenizer.sep_token
    unk_token = tokenizer.unk_token
    pad_token_id = tokenizer.pad_token_id

    input_ids_batch = []
    attention_mask_batch = []
    token_type_ids_batch = []
    slot_label_mask_batch = []
    max_seq_len = 50
    device = "cuda" if torch.cuda.is_available() else "cpu"

    for text in texts:
        tokens = []
        slot_label_mask = []
        for word in text.split():
            word_tokens = tokenizer.tokenize(word)
            if not word_tokens:
                word_tokens = [unk_token]  # For handling the bad-encoded word
            tokens.extend(word_tokens)
            # Real label position as 0 for the first token of the word, and padding ids for the remaining tokens
            slot_label_mask.extend([0] + [pad_token_label_id] * (len(word_tokens) - 1))

        # Account for [CLS] and [SEP]
        special_tokens_count = 2
        if len(tokens) > max_seq_len - special_tokens_count:
            tokens = tokens[:(max_seq_len - special_tokens_count)]
            slot_label_mask = slot_label_mask[:(max_seq_len - special_tokens_count)]

        # Add [SEP] token
        tokens += [sep_token]
        slot_label_mask += [pad_token_label_id]
        token_type_ids = [sequence_a_segment_id] * len(tokens)

        # Add [CLS] token
        tokens = [cls_token] + tokens
        slot_label_mask = [pad_token_label_id] + slot_label_mask
        token_type_ids = [cls_token_segment_id] + token_type_ids

        input_ids = tokenizer.convert_tokens_to_ids(tokens)

        # The mask has 1 for real tokens and 0 for padding tokens. Only real
        # tokens are attended to.
        attention_mask = [1 if mask_padding_with_zero else 0] * len(input_ids)

        # Zero-pad up to the sequence length.
        padding_length = max_seq_len - len(input_ids)
        input_ids = input_ids + ([pad_token_id] * padding_length)
        attention_mask = attention_mask + ([0 if mask_padding_with_zero else 1] * padding_length)
        token_type_ids = token_type_ids + ([pad_token_segment_id] * padding_length)
        slot_label_mask = slot_label_mask + ([pad_token_label_id] * padding_length)

        input_ids_batch.append(input_ids)
        attention_mask_batch.append(attention_mask)
        token_type_ids_batch.append(token_type_ids)
        slot_label_mask_batch.append(slot_label_mask)

    # Making tensor that is batch size of 1
    input_ids_batch = torch.tensor(input_ids_batch, dtype=torch.long).to(device)
    attention_mask_batch = torch.tensor(attention_mask_batch, dtype=torch.long).to(device)
    token_type_ids_batch = torch.tensor(token_type_ids_batch, dtype=torch.long).to(device)
    slot_label_mask_batch = torch.tensor(slot_label_mask_batch, dtype=torch.long).to(device)

    return input_ids_batch, attention_mask_batch, token_type_ids_batch, slot_label_mask_batch


def predict(orig_texts, mod):
    res = None
    intent_label_lst = ["UNK", "Navigation"]
    slot_label_lst = ["PAD", "UNK", "O", "B-first.loc", "I-first.loc", "B-first.dis", "I-first.dis", "B-second.loc",
                      "I-second.loc", "B-second.dis", "I-second.dis",
                      "B-third.loc", "I-third.loc", "B-third.dis", "I-third.dis", "B-fourth.loc", "I-fourth.loc",
                      "B-fourth.dis", "I-fourth.dis", "B-fifth.loc",
                      "B-fifth.dis", "I-fifth.dis"]
    pad_token_label_id = 100
    texts = []
    tokenizer = load_tokenizer()
    for cased_text in orig_texts:
        texts.append(cased_text.lower())

    batch = _convert_texts_to_tensors(texts, tokenizer)

    slot_label_mask = batch[3]

    mod.eval()

    # We have only one batch
    with torch.no_grad():
        inputs = {'input_ids': batch[0],
                  'attention_mask': batch[1],
                  'intent_label_ids': torch.rand(1).long(),
                  'slot_labels_ids': torch.rand(1, 50).long()}
        inputs['input'] = batch[2]
        outputs = mod(**inputs)
        _, (intent_logits, slot_logits) = outputs[:2]  # loss doesn't needed

    # Intent prediction
    intent_preds = intent_logits.detach().cpu().numpy()
    intent_preds = np.argmax(intent_preds, axis=1)
    intent_list = []
    for intent_idx in intent_preds:
        intent_list.append(intent_label_lst[intent_idx])

    # Slot prediction
    slot_preds = slot_logits.detach().cpu().numpy()
    slot_preds = np.argmax(slot_preds, axis=2)

    out_slot_labels_ids = slot_label_mask.detach().cpu().numpy()

    slot_label_map = {i: label for i, label in enumerate(slot_label_lst)}
    slot_preds_list = [[] for _ in range(out_slot_labels_ids.shape[0])]

    for i in range(out_slot_labels_ids.shape[0]):
        for j in range(out_slot_labels_ids.shape[1]):
            if out_slot_labels_ids[i, j] != pad_token_label_id:
                slot_preds_list[i].append(slot_label_map[slot_preds[i][j]])
    for text, intent, slots in zip(orig_texts, intent_list, slot_preds_list):
        print(text)
        print(intent)
        print(slots)
        res = ' '.join(slots)
    return res


def clue_len(res):
    length = -1
    if 'B-fifth.loc' in res:
        length = 5
    elif 'B-fourth.loc' in res:
        length = 4
    elif 'B-third.loc' in res:
        length = 3
    elif 'B-second.loc' in res:
        length = 2
    elif 'B-first.loc' in res:
        length = 1
    else:
        length = 0
    return length


def num_to_str(num):
    if num == 1:
        return 'first'
    elif num == 2:
        return 'second'
    elif num == 3:
        return 'third'
    elif num == 4:
        return 'fourth'
    elif num == 5:
        return 'fifth'
    else:
        return ''


def find_target(orig_texts, res, target):
    orig_texts_list = orig_texts.split(' ')
    res_list = res.split(' ')
    ans = ''
    for i in range(len(res_list)):
        if res_list[i] == target:
            ans = ans + orig_texts_list[i] + ' '
    return ans


# 此方法处理用户注册
@app.route('/demo', methods=['POST'])
def demo():
    orig_texts = request.form['orig_texts']
    print(orig_texts)
    currPath = os.path.dirname(os.path.abspath(__file__))
    model = torch.jit.load(currPath + "\\CompressJointBert.pt")
    orig_texts = [orig_texts.strip()]
    res = predict(orig_texts, model)
    length = clue_len(res)
    clue = '%d#' % length
    index = 1
    orig_texts = ''.join(orig_texts)
    while index <= length:
        clue = clue + find_target(orig_texts, res, 'B-' + num_to_str(index) + '.loc')
        tmp = find_target(orig_texts, res, 'I-' + num_to_str(index) + '.loc')
        if tmp != '':
            clue = clue + tmp + '#'
        else:
            clue = clue + '#'
        tmp = find_target(orig_texts, res, 'B-' + num_to_str(index) + '.dis')
        if tmp != '':
            if find_target(orig_texts, res, 'I-' + num_to_str(index) + '.dis') != '':
                tmp = tmp + find_target(orig_texts, res, 'I-' + num_to_str(index) + '.dis')
                tmp = re.findall(r"\d+\.?\d*", tmp)
                tmp = ''.join(tmp)
            else:
                tmp = re.findall(r"\d+\.?\d*", tmp)
                tmp = ''.join(tmp)
            clue = clue + tmp + '#'
        else:
            clue = clue + '-1' + '#'
        index = index + 1
    print(clue)
    return clue


if __name__ == '__main__':
    # app.run(host='222.20.77.153', port=5000)
    app.run(host='222.20.76.149', port=5000)